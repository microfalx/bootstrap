package net.microfalx.bootstrap.search;

import net.microfalx.bootstrap.content.ContentService;
import net.microfalx.bootstrap.core.async.ThreadPoolFactory;
import net.microfalx.bootstrap.core.i18n.I18nService;
import net.microfalx.bootstrap.resource.ResourceService;
import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.ConcurrencyUtils;
import net.microfalx.lang.StringUtils;
import net.microfalx.lang.TimeUtils;
import net.microfalx.lang.annotation.SizeOf;
import net.microfalx.metrics.Matrix;
import net.microfalx.metrics.Timer;
import net.microfalx.threadpool.ThreadPool;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static java.util.Collections.emptySet;
import static net.microfalx.bootstrap.search.SearchUtils.*;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;
import static net.microfalx.lang.ExceptionUtils.rethrowExceptionAndReturn;
import static net.microfalx.lang.FormatterUtils.formatDuration;
import static net.microfalx.lang.FormatterUtils.formatNumber;
import static net.microfalx.lang.StringUtils.*;
import static net.microfalx.lang.TimeUtils.*;

/**
 * A service used to execute full text searches.
 */
@Service
@SizeOf(deepSize = SizeOf.UNESTIMATED_SIZE)





























































public class SearchService implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchService.class);

    @Autowired(required = false)
    private SearchProperties searchProperties = new SearchProperties();

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private I18nService i18nService;

    @Autowired
    private ContentService contentService;

    private volatile ThreadPool threadPool;

    private final Object lock = new Object();
    private volatile Searcher searcher;

    private final Map<String, String> attributeClasses = new ConcurrentHashMap<>();
    private final Collection<SearchListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, String> labels = new ConcurrentHashMap<>();
    private final Map<String, String> description = new ConcurrentHashMap<>();
    private volatile Map<String, FieldStatistics> fieldStatistics = new HashMap<>();
    private volatile long lastFieldLoad = TimeUtils.oneDayAgo();
    private final AtomicBoolean fieldLoadingFlag = new AtomicBoolean();
    private final CountDownLatch fieldLoadingLatch = new CountDownLatch(1);

    /**
     * Returns the service used to access the content of the documents.
     * @return a non-null instance
     */
    public ContentService getContentService() {
        return contentService;
    }

    /**
     * Returns the executor used by the search service.
     *
     * @return a non-null instance
     */
    public ThreadPool getThreadPool() {
        return threadPool;
    }

    /**
     * Registers a search listener.
     *
     * @param listener the listener to register
     */
    public void registerListener(SearchListener listener) {
        requireNonNull(listener);
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            LOGGER.debug("Registered search listener {}", ClassUtils.getName(listener));
        } else {
            LOGGER.warn("Search listener already registered {}", ClassUtils.getName(listener));
        }
    }

    /**
     * Returns all fields available in the index.
     *
     * @return a non-null instance
     */
    public Collection<FieldStatistics> getFieldStatistics() {
        if (millisSince(lastFieldLoad) > ONE_HOUR && fieldLoadingFlag.compareAndSet(false, true)) {
            try {
                getThreadPool().submit(new ExtractFieldStatsWorker());
            } catch (Exception e) {
                fieldLoadingFlag.set(false);
            }
        }
        ConcurrencyUtils.await(fieldLoadingLatch, Duration.ofSeconds(10));
        List<FieldStatistics> statistics = new ArrayList<>(fieldStatistics.values());
        statistics.sort(Comparator.comparing(FieldStatistics::getDocumentCount).reversed());
        return statistics;
    }

    /**
     * Returns all fields available in the index.
     *
     * @return a non-null instance
     */
    public Collection<FieldStatistics> getFieldStatistics(int top) {
        List<FieldStatistics> fieldStatistics = new ArrayList<>(getFieldStatistics());
        if (top <= 0) return fieldStatistics;
        fieldStatistics.sort(Comparator.comparing(FieldStatistics::getTermCount).reversed());
        if (fieldStatistics.size() < top) return fieldStatistics;
        return fieldStatistics.subList(0, top);
    }

    /**
     * Returns statistics about a field.
     *
     * @param name the field name
     * @return the statistics
     */
    public FieldStatistics getFieldStatistics(String name) {
        requireNonNull(name);
        getFieldStatistics();
        return fieldStatistics.computeIfAbsent(toIdentifier(name), s -> new FieldStatistics(name));
    }

    /**
     * Finds one document by id.
     *
     * @param id the document identifier.
     * @return the document, null if such
     */
    public Document find(String id) {
        requireNonNull(id);
        LOGGER.info("Searching for document with identifier '{}'", id);
        try {
            RetryTemplate retryTemplate = new RetryTemplate();
            retryTemplate.registerListener(new RetryListener() {
                @Override
                public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                    LOGGER.info("Failed to find document with id {}, root cause: {}", id, getRootCauseDescription(throwable));
                    releaseSearcher();
                }
            });
            return retryTemplate.execute((RetryCallback<Document, Exception>) context -> doFind(id));
        } catch (Exception e) {
            throw new SearchException("Exception during find for '" + id + "'", e);
        }
    }

    /**
     * Search the index for all items matching the query.
     *
     * @param query the query information
     * @return matching items
     * @throws SearchException if the query cannot be executed
     */
    public SearchResult search(SearchQuery query) {
        requireNonNull(query);
        try {
            Query parsedQuery = createQuery(query, "Searching");
            RetryTemplate retryTemplate = createTemplate(query);
            return retryTemplate.execute((RetryCallback<SearchResult, Exception>) context -> doSearch(parsedQuery, query));
        } catch (IndexNotFoundException e) {
            return new SearchResult(query);
        } catch (SearchException e) {
            throw e;
        } catch (Exception e) {
            throw new SearchException("Exception during search for : " + query, e);
        }
    }

    /**
     * Triggers a reload of the index.
     * <p>
     * Next search will see latest's documents available in the index.
     */
    public void reload() {
        releaseSearcher();
    }

    /**
     * Extracts field trends for all documents matching the query.
     * <p>
     * The modified time of the document (which is the same as creation time if the document was not changed) will
     * be used.
     * <p>
     * If a time window is not provided, the last 24h will be used.
     *
     * @param query          the query
     * @param timestampField the field which holds the timestamp
     * @return the trends
     */
    public Collection<Matrix> getFieldsTrends(SearchQuery query, String timestampField, Duration step) {
        return getFieldsTrends(query, timestampField, emptySet(), step);
    }

    /**
     * Extracts field trends for all documents matching the query.
     * <p>
     * A timestamp field is required to create the trends. If not provided, or the document does not have this field,
     * the modified time will be used (or creation time if document was not modified).
     * <p>
     * If a time window is not provided, the last 24h will be used.
     *
     * @param query          the query
     * @param timestampField the field which holds the timestamp
     * @param fields         a list of field to extract trend for; if empty, extract all non-standard fields
     * @return the trends
     */
    public Collection<Matrix> getFieldsTrends(SearchQuery query, String timestampField, Set<String> fields, Duration step) {
        requireNonNull(query);
        try {
            if (query.getStartTime() == null) query.setStartTime(ZonedDateTime.now().minusHours(24));
            Query parsedQuery = createQuery(query, "Extract field trends");
            RetryTemplate retryTemplate = createTemplate(query);
            return retryTemplate.execute((RetryCallback<Collection<Matrix>, Exception>) context
                    -> doGetFieldsTrends(parsedQuery, timestampField, fields, step));
        } catch (SearchException e) {
            throw e;
        } catch (Exception e) {
            throw new SearchException("Exception during fields trend extraction for : " + query, e);
        }
    }

    /**
     * Extracts document trends for all documents matching the query.
     * <p>
     * The modified time of the document (which is the same as creation time if the document was not changed) will
     * be used.
     * <p>
     * If a time window is not provided, the last 24h will be used.
     *
     * @param query the query
     * @return the trends
     */
    public Matrix getDocumentTrends(SearchQuery query, Duration step) {
        return getDocumentTrends(query, Document.MODIFIED_AT_FIELD, step);
    }

    /**
     * Extracts document trends for all documents matching the query.
     * <p>
     * A timestamp field is required to create the trends. If not provided, or the document does not have this field,
     * the modified time will be used (or creation time if document was not modified).
     * <p>
     * If a time window is not provided, the last 24h will be used.
     *
     * @param query          the query
     * @param timestampField the field which holds the timestamp
     * @return the trends
     */
    public Matrix getDocumentTrends(SearchQuery query, String timestampField, Duration step) {
        requireNonNull(query);
        try {
            if (query.getStartTime() == null) query.setStartTime(ZonedDateTime.now().minusHours(24));
            Query parsedQuery = createQuery(query, "Extract document trends");
            RetryTemplate retryTemplate = createTemplate(query);
            return retryTemplate.execute((RetryCallback<Matrix, Exception>) context -> doGetDocumentTrend(parsedQuery, timestampField, step));
        } catch (SearchException e) {
            throw e;
        } catch (Exception e) {
            throw new SearchException("Exception during fields trend extraction for : " + query, e);
        }
    }

    /**
     * Returns the description of the item with searched terms highlighted.
     *
     * @param query  the query
     * @param itemId the item id to highlight
     * @return the text
     */
    public String getHighlightedText(SearchQuery query, String itemId) {
        return null;
    }

    /**
     * Returns the label associated with a field.
     *
     * @param field the field
     * @return the label
     */
    public String getLabel(String field) {
        requireNonNull(field);
        String label = labels.get(field);
        if (label != null) return label;
        for (SearchListener listener : listeners) {
            label = listener.getLabel(field);
            if (StringUtils.isNotEmpty(label)) break;
        }
        if (label == null) label = getI18n("label");
        if (label == null) label = StringUtils.capitalizeWords(field);
        labels.put(field, label);
        return label;
    }

    /**
     * Returns the description associated with a field.
     *
     * @param field the field
     * @return the label
     */
    public String getDescription(String field) {
        requireNonNull(field);
        String label = labels.get(field);
        if (label != null) return label;
        for (SearchListener listener : listeners) {
            label = listener.getDescription(field);
            if (StringUtils.isNotEmpty(label)) break;
        }
        if (label == null) label = getI18n("description");
        labels.put(field, label);
        return label;
    }

    /**
     * Returns whether the attribute will be displayed in the search result.
     *
     * @param document  the search engine document
     * @param attribute the attribute
     * @return {@code true} if accepted, {@code false} otherwise
     */
    public boolean accept(Document document, Attribute attribute) {
        requireNonNull(document);
        requireNonNull(attribute);
        for (SearchListener listener : listeners) {
            if (!listener.accept(document, attribute)) return false;
        }
        return true;
    }

    /**
     * Returns a searcher which carries an {@link org.apache.lucene.search.IndexSearcher} and associated
     * objects.
     *
     * @param options  the options to use for search operations
     * @return a non-null instance
     * @throws SearchException if the index cannot be opened
     */
    public Searcher createSearcher(SearcherOptions options) {
        requireNonNull(options);
        SearchUtils.updateOptions(resourceService, getThreadPool(), options);
        LOGGER.debug("Open searcher");
        try {
            return SEARCH_METRICS.timeCallable("Open", () -> new Searcher(options.getDirectory(), options));
        } catch (Exception e) {
            return rethrowExceptionAndReturn(e);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initListeners();
        initTaskExecutor();
        initTasks();
    }

    private <T> T doWithIndex(String operation, Function<IndexReader, T> callback) {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.registerListener(new RetryListener() {
            @Override
            public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                LOGGER.info("Failure detected during action '{}', root cause: {}", operation, getRootCauseDescription(throwable));
                releaseSearcher();
            }
        });
        try {
            return retryTemplate.execute((RetryCallback<T, Exception>) context -> {
                IndexReader indexReader = getIndexSearcher().getIndexReader();
                return SEARCH_METRICS.time(toIdentifier(capitalizeWords(operation)), () -> callback.apply(indexReader));
            });
        } catch (Exception e) {
            throw new SearchException("Exception during index operation : " + operation, e);
        }
    }

    private String getNormalizedQuery(SearchQuery query) {
        // String normalizedQuery = SearchUtils.normalizeQuery(query.getQuery(), query.isAutoWildcard(), query.isAllowLeadingWildcard()).trim();
        String normalizedQuery = query.getQuery();
        if (isNotEmpty(query.getFilter())) {
            normalizedQuery = "(" + normalizedQuery + ") AND (" + query.getFilter() + ")";
        }
        return normalizedQuery;
    }

    private Query createQuery(SearchQuery query, String logPrefix) throws ParseException {
        try {
            final String normalizedQuery = getNormalizedQuery(query);
            final QueryParser queryParser = createQueryParser();
            queryParser.setAllowLeadingWildcard(query.isAllowLeadingWildcard() && searchProperties.isAllowLeadingWildcard());
            Query parsedQuery = null;
            if (StringUtils.isNotEmpty(normalizedQuery)) parsedQuery = queryParser.parse(normalizedQuery);
            Query timeQuery = null;
            if (query.getStartTime() != null) {
                ZonedDateTime endTime = query.getEndTime() != null ? query.getEndTime() : ZonedDateTime.now();
                timeQuery = LongPoint.newRangeQuery(Document.MODIFIED_AT_FIELD, toMillis(query.getStartTime()), toMillis(endTime));
            }
            Query finalQuery;
            if (parsedQuery == null && timeQuery == null) {
                finalQuery = new MatchAllDocsQuery();
            } else if (parsedQuery != null && timeQuery == null) {
                finalQuery = parsedQuery;
            } else if (parsedQuery == null) {
                finalQuery = timeQuery;
            } else {
                finalQuery = new BooleanQuery.Builder().add(parsedQuery, BooleanClause.Occur.MUST)
                        .add(timeQuery, BooleanClause.Occur.MUST).build();
            }
            if (logPrefix != null) {
                LOGGER.info(logPrefix + "with '" + query.getDescription() + "', normalized query: '" + getNormalizedQuery(query) + "', parsed query: '" + finalQuery
                        + "', auto-wildcard: " + query.isAutoWildcard() + ", leading wildcard: " + query.isAllowLeadingWildcard());
            }
            return finalQuery;
        } catch (ParseException e) {
            throw new SearchException("Failed to parse query string: " + query, e);
        }
    }

    private Document doFind(String id) throws IOException {
        final IndexSearcher indexSearcher = getIndexSearcher();
        Document translatedDocument = SEARCH_METRICS.timeCallable("Find", () -> {
            TermQuery query = new TermQuery(new Term(Document.ID_FIELD, id));
            TopDocs topDocs = indexSearcher.search(query, 1);
            DocumentMapper documentMapper = new DocumentMapper(contentService);
            Document mappedDocument = null;
            if (topDocs.scoreDocs.length > 0) {
                org.apache.lucene.document.Document document = indexSearcher.storedFields().document(topDocs.scoreDocs[0].doc);
                mappedDocument = documentMapper.read(document);
            }
            return mappedDocument;
        });
        if (translatedDocument != null) {
            LOGGER.info("Found document with identifier " + id + ", name " + translatedDocument.getName());
        }
        return translatedDocument;
    }

    private Collection<Matrix> doGetFieldsTrends(Query luceneQuery, String timestampField, Set<String> fields, Duration step) throws IOException {
        final IndexSearcher indexSearcher = getIndexSearcher();
        final FieldTrendCollector.Manager manager = new FieldTrendCollector.Manager(timestampField, fields, step);
        SEARCH_METRICS.timeCallable("Extract Field Trends", () -> {
            indexSearcher.search(luceneQuery, manager);
            return null;
        });
        LOGGER.info("Found " + manager.getTrends().size() + " field trends in " + formatNumber(manager.getMatchingDocCount()) + " matching documents, " +
                "total documents " + formatNumber(manager.getDocCount()) + ", took " + formatDuration(Timer.last().getDuration()));
        return manager.getTrends();
    }

    private Matrix doGetDocumentTrend(Query luceneQuery, String timestampField, Duration step) throws IOException {
        final IndexSearcher indexSearcher = getIndexSearcher();
        final DocumentTrendCollector.Manager manager = new DocumentTrendCollector.Manager(timestampField, step);
        SEARCH_METRICS.timeCallable("Extract Document Trends", () -> {
            indexSearcher.search(luceneQuery, manager);
            return null;
        });
        LOGGER.info("Found " + manager.getTrend().getCount() + " document trends in " + formatNumber(manager.getMatchingDocCount()) + " matching documents, " +
                "total documents " + formatNumber(manager.getDocCount()) + ", took " + formatDuration(Timer.last().getDuration()));
        return manager.getTrend();
    }

    @SuppressWarnings("resource")
    private SearchResult doSearch(Query luceneQuery, SearchQuery searchQuery) throws IOException {
        final SearchResult result = new SearchResult(searchQuery);
        result.setRewriteQuery(luceneQuery.toString());
        final IndexSearcher indexSearcher = getIndexSearcher();
        final StoredFields storedFields = indexSearcher.storedFields();
        final List<Document> items = new ArrayList<>();
        final Sort sort = createSort(searchQuery);
        TopDocs topDocs = SEARCH_METRICS.timeCallable("Search", () -> {
            TopDocs docs = indexSearcher.search(luceneQuery, searchQuery.getStart() + searchQuery.getLimit(), sort);
            int startIndex = searchQuery.getStart();
            int counter = searchQuery.getLimit();
            DocumentMapper documentMapper = new DocumentMapper(contentService);
            for (ScoreDoc scoreDoc : docs.scoreDocs) {
                if (startIndex-- > 0) continue;
                org.apache.lucene.document.Document document = storedFields.document(scoreDoc.doc);
                Document translatedDocument = documentMapper.read(document);
                translatedDocument.setRelevance(Float.isNaN(scoreDoc.score) ? Document.NO_RELEVANCE : scoreDoc.score);
                items.add(translatedDocument);
                if (counter-- == 0) break;
            }
            return docs;
        });
        LOGGER.info("Found {} total hit(s), took {}", topDocs.totalHits, formatDuration(Timer.last().getDuration()));
        result.setDocuments(items);
        result.setTotalHits(topDocs.totalHits.value());
        return result;
    }

    private Sort createSort(SearchQuery searchQuery) {
        SearchQuery.Sort querySort = searchQuery.getSort();
        if (querySort.getType() == SearchQuery.Sort.Type.FIELD) {
            String field = querySort.getField();
            field = isNumericField(field) ? field + Document.SORTED_SUFFIX_FIELD : field;
            SortField sortField = new SortedNumericSortField(field, SortField.Type.LONG, querySort.isReversed());
            return new Sort(sortField);
        } else if (querySort.getType() == SearchQuery.Sort.Type.RELEVANCE) {
            return Sort.RELEVANCE;
        } else if (querySort.getType() == SearchQuery.Sort.Type.INDEX_ORDER) {
            return Sort.INDEXORDER;
        } else {
            throw new SearchException("Unknown sort type: " + querySort.getType());
        }
    }

    private IndexSearcher getIndexSearcher() {
        return getSearcher(false).getSearcher();
    }

    /**
     * Returns an structure which carries an {@link org.apache.lucene.search.IndexSearcher} and associated
     * objects.
     *
     * @param reopen <code>true</code> to re-open the index reader, <code>false</code> to reuse the index reader
     * @return a non-null instance
     * @throws SearchException if the index cannot be opened
     */
    private Searcher getSearcher(boolean reopen) {
        synchronized (lock) {
            reopen = reopen || (searcher != null && searcher.isStale());
            if (searcher == null || reopen) {
                if (searcher != null) releaseSearcher();
                SearcherOptions options = getSearcherOptions();
                searcher = createSearcher(options);
            }
            return searcher;
        }
    }

    private void releaseSearcher() {
        LOGGER.debug("Release searcher");
        synchronized (lock) {
            if (searcher != null) {
                searcher.release();
                searcher = null;
            }
        }
    }

    private RetryTemplate createTemplate(SearchQuery searchQuery) {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.registerListener(new RetryListener() {
            @Override
            public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                LOGGER.info("Failed to search for '" + searchQuery + ", root cause: " + getRootCauseDescription(throwable));
                releaseSearcher();
            }
        });
        return retryTemplate;
    }

    private QueryParser createQueryParser() {
        final QueryParser queryParser = Analyzers.createQueryParser(SearchUtils.DEFAULT_FIELD);
        queryParser.setAllowLeadingWildcard(searchProperties.isAllowLeadingWildcard());
        return queryParser;
    }

    private void initListeners() {
        Collection<SearchListener> discoveredListeners = ClassUtils.resolveProviderInstances(SearchListener.class);
        LOGGER.info("Register {} search listeners", discoveredListeners.size());
        for (SearchListener discoveredListener : discoveredListeners) {
            LOGGER.debug(" - {}", ClassUtils.getName(discoveredListener));
            registerListener(discoveredListener);
        }
    }

    private void initTaskExecutor() {
        threadPool = ThreadPoolFactory.create("Searcher").setRatio(0.5f).create();
    }

    private void initTasks() {
        threadPool.submit(() -> getFieldStatistics());
    }

    private SearcherOptions getSearcherOptions() {
        return (SearcherOptions) SearcherOptions.create(INDEX_NAME)
                .refreshInterval(searchProperties.getRefreshInterval())
                .threadPool(getThreadPool())
                .build();
    }

    private String getI18n(String suffix) {
        return i18nService.getText("search.field." + suffix);
    }

    private class ExtractFieldStatsWorker implements Runnable {

        @Override
        public void run() {
            try {
                fieldStatistics = doWithIndex("Get Fields", indexReader -> {
                    Map<String, FieldStatistics> fieldStatistics = new HashMap<>();
                    SearchUtils.extractFieldsAndTerms(indexReader, fieldStatistics, MAX_TERMS_PER_FIELD);
                    return fieldStatistics;
                });
            } finally {
                fieldLoadingFlag.set(false);
                fieldLoadingLatch.countDown();
            }
            lastFieldLoad = System.currentTimeMillis();
        }
    }

}
