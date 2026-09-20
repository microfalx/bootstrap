package net.microfalx.bootstrap.search;

import net.microfalx.bootstrap.content.ContentService;
import net.microfalx.bootstrap.core.async.ThreadPoolFactory;
import net.microfalx.bootstrap.resource.ResourceService;
import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.FormatterUtils;
import net.microfalx.lang.ObjectUtils;
import net.microfalx.lang.annotation.SizeOf;
import net.microfalx.threadpool.ThreadPool;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.index.*;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.InfoStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.time.Duration.ofSeconds;
import static java.util.Collections.singleton;
import static java.util.Collections.unmodifiableCollection;
import static net.microfalx.bootstrap.search.SearchUtils.*;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;
import static net.microfalx.lang.ExceptionUtils.rethrowExceptionAndReturn;
import static net.microfalx.lang.StringUtils.toIdentifier;

/**
 * Provides indexing capabilities for full text search.
 */
@Service
@SizeOf(deepSize = SizeOf.UNESTIMATED_SIZE)
public class IndexService implements InitializingBean, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexService.class);

    @Autowired(required = false) private SearchProperties searchProperties = new SearchProperties();
    @Autowired(required = false) private IndexProperties indexProperties = new IndexProperties();
    @Autowired private ResourceService resourceService;
    @Autowired private ContentService contentService;

    private volatile ThreadPool threadPool;

    private final Lock lock = new ReentrantLock();
    private final Collection<IndexListener> listeners = new CopyOnWriteArrayList<>();
    private final Queue<Collection<Document>> pendingDocuments = new ArrayBlockingQueue<>(500);
    private final Map<String, Indexer> indexers = new ConcurrentHashMap<>();
    private final Map<String, Lock> locks = new ConcurrentHashMap<>();
    private final Set<String> indexersOpened = new CopyOnWriteArraySet<>();
    private volatile Indexer indexer;

    /**
     * Returns the indexer with the specified id.
     *
     * @param id the id of the indexer to return
     * @return a non-null instance of {@link Indexer}
     */
    public Indexer getIndexer(String id) {
        requireNonNull(id);
        Indexer indexer = indexers.get(toIdentifier(id));
        if (indexer == null) {
            throw new IllegalArgumentException("No indexer found with id: " + id);
        }
        return indexer;
    }

    /**
     * Returns a collection of indexes registered with this service.
     *
     * @return a non-null instance
     */
    public Collection<Indexer> getIndexers() {
        return unmodifiableCollection(indexers.values());
    }

    /**
     * Returns the service used to access the content of the documents.
     *
     * @return a non-null instance
     */
    public ContentService getContentService() {
        return contentService;
    }

    /**
     * Returns the thread pool used by the index service.
     *
     * @return a non-null instance
     */
    public ThreadPool getThreadPool() {
        if (threadPool == null) initTaskExecutor();
        return threadPool;
    }

    /**
     * Registers an index listener.
     *
     * @param listener the listener to register
     */
    public void registerListener(IndexListener listener) {
        requireNonNull(listener);
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            LOGGER.debug("Registered index listener {}", ClassUtils.getName(listener));
        } else {
            LOGGER.warn("Index listener already registered {}", ClassUtils.getName(listener));
        }
    }

    /**
     * Indexes a document and commits at the end.
     *
     * @param document the document to index
     * @throws IndexException if the document cannot be indexed
     */
    public void index(Document document) {
        index(document, false);
    }

    /**
     * Indexes a document and commits at the end.
     *
     * @param document the document to index
     * @param commit   true - wait to commit in batched documents, false - commit immediately
     * @throws IndexException if the document cannot be indexed
     */
    public void index(Document document, boolean commit) {
        requireNonNull(document);
        index(singleton(document), commit);
    }

    /**
     * Indexes a collection of documents and commits at the end.
     *
     * @param documents the collections of documents to index
     * @throws IndexException if the document cannot be indexed
     */
    public void index(Collection<Document> documents) {
        index(documents, false);
    }

    /**
     * Returns a count with documents in the index.
     *
     * @return a positive integer
     */
    public long getDocumentCount() {
        return openIndexer().getDocumentCount();
    }

    /**
     * Returns a count with documents pending in memory.
     *
     * @return a positive integer
     */
    public int getPendingDocumentCount() {
        return openIndexer().getPendingDocumentCount();
    }

    /**
     * Forces all documents in memory to be flushed on disk.
     */
    public void flush() {
        commitIndex();
    }

    /**
     * Indexes a collection of documents.
     *
     * @param documents the collections of documents to index
     * @param commit    true - wait to commit in batched documents, false - commit immediately
     * @throws IndexException if the document cannot be indexed
     */
    public void index(Collection<Document> documents, boolean commit) {
        requireNonNull(documents);
        try {
            doWithIndex("Index", indexWriter -> {
                DocumentMapper itemMapper = new DocumentMapper(contentService);
                for (Document document : documents) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(" - index item, id: " + document.getId() + ", name: " + document.getName());
                    }
                    itemMapper.write(indexWriter, document);
                }
                return null;
            });
            boolean isEmpty = pendingDocuments.isEmpty();
            pendingDocuments.offer(documents);
            if (isEmpty) getThreadPool().execute(new IndexedDocumentsTask());
        } catch (Exception e) {
            throw new IndexException("Failed to index collection", e);
        } finally {
            try {
                markIndexChanged(commit);
            } catch (Exception e) {
                LOGGER.error("Failed to commit index", e);
            }
        }
    }

    /**
     * Removes a document with from the index.
     * <p/>
     * Item removal is asynchronous, index will be committed later.
     *
     * @param itemId the id of the item to remove
     */
    public void remove(String itemId) {
        requireNonNull(itemId);
        doWithIndex("Delete", indexWriter -> {
            LOGGER.info("Deleting item with id: " + itemId);
            return indexWriter.deleteDocuments(new Term(Document.ID_FIELD, itemId));
        });
    }

    /**
     * Clear the index.
     */
    public synchronized void clear() {
        File directory = null;
        if (indexer != null) directory = indexer.getOptions().getDirectory();
        releaseIndex();
        try {
            if (directory != null) FileUtils.deleteDirectory(directory);
        } catch (IOException e) {
            LOGGER.atError().setCause(e).log("Failed to remove index {}", directory);
        }
    }

    /**
     * Commits any pending changes.
     */
    public void commit() {
        commitIndex();
    }

    /**
     * Creates the index.
     *
     * @param options the options for the indexer
     */
    public Indexer createIndexer(IndexerOptions options) {
        requireNonNull(options);
        SearchUtils.updateOptions(resourceService, getThreadPool(), options);
        IndexWriterConfig.OpenMode openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND;
        if (options.isRecreate()) openMode = IndexWriterConfig.OpenMode.CREATE;
        IndexWriterConfig writerConfig = new IndexWriterConfig(options.getAnalyzer());
        writerConfig.setOpenMode(openMode);
        writerConfig.setIndexDeletionPolicy(new KeepOnlyLastCommitDeletionPolicy());
        writerConfig.setMergeScheduler(new ConcurrentMergeScheduler());
        writerConfig.setUseCompoundFile(true);
        writerConfig.setRAMBufferSizeMB(getRAMBufferSizeMB());
        writerConfig.setRAMPerThreadHardLimitMB(getRAMBPerThreadBufferSizeMB());
        Lock indexLock = getIndexLock(options.getId());
        indexLock.lock();
        try {
            // on startup, make sure if there is a leftover lock file, remove it
            if (indexersOpened.add(options.getId())) unlockIndexDirectory(options);
            if (LOGGER.isDebugEnabled()) writerConfig.setInfoStream(new LoggerInfoStream());
            try {
                Directory luceneDirectory = FSDirectory.open(options.getDirectory().toPath());
                IndexWriter indexWriter = new IndexWriter(luceneDirectory, writerConfig);
                if (openMode == IndexWriterConfig.OpenMode.CREATE) indexWriter.commit();
                LOGGER.debug("Create index writer, RAM Buffer " + writerConfig.getRAMBufferSizeMB() + " MB" +
                        ", Thread RAM Buffer " + writerConfig.getRAMPerThreadHardLimitMB() + " MB" +
                        ", Use compound files " + writerConfig.getUseCompoundFile());
                Indexer indexer = new Indexer(indexWriter, luceneDirectory, options);
                this.indexers.put(indexer.getOptions().getId(), indexer);
                return indexer;
            } catch (IOException e) {
                if (e instanceof LockObtainFailedException) {
                    unlockIndexDirectory(options);
                }
                throw new IndexException("Failed to create the index", e);
            }
        } finally {
            indexLock.unlock();
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initListeners();
        initTaskExecutor();
        initTasks();
        logSettings();
        openIndexer();
    }

    @Override
    public void destroy() {
        commitIndex();
        releaseIndex();
    }

    /**
     * Returns the maximum memory used by RAM Directories.
     *
     * @return maximum memory in MB
     */
    private int getRAMBufferSizeMB() {
        int maxRamMB = (int) (Runtime.getRuntime().maxMemory() / 4) / (1024 * 1024);
        maxRamMB = Math.max(16, maxRamMB);
        maxRamMB = Math.min(100, maxRamMB);
        return indexProperties.getRamBufferSize() > 0 ? (int) indexProperties.getRamBufferSize() : maxRamMB;
    }

    /**
     * Returns the maximum memory used by RAM Directories per thread.
     *
     * @return maximum memory in MB
     */
    private int getRAMBPerThreadBufferSizeMB() {
        int ramBufferSizeMB = getRAMBufferSizeMB();
        return indexProperties.getRamBufferSizeThread() > 0 ? (int) indexProperties.getRamBufferSizeThread() : ramBufferSizeMB / 10;
    }

    /**
     * Returns the index
     *
     * @return the index, null if not available
     */
    private Indexer getIndexer() {
        lock.lock();
        try {
            return indexer;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Opens the index, if not already opened.
     *
     * @return a non-null instance
     */
    private Indexer openIndexer() {
        lock.lock();
        try {
            if (indexer != null) return indexer;
            try {
                IndexerOptions options = createIndexOptions();
                indexer = INDEX_METRICS.time("Open", () -> createIndexer(options));
                return indexer;
            } catch (Exception e) {
                if (indexer != null) indexer.release();
                return rethrowExceptionAndReturn(e);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Performs an operation on an index.
     *
     * @param name     the name of the operation
     * @param callback the index callback
     */
    private <R> R doWithIndex(String name, Indexer.Callback<R> callback) {
        try {
            RetryTemplate template = createRetryTemplate();
            return template.execute(context -> {
                Indexer index = openIndexer();
                return index.doWithIndex(name, callback);
            });
        } catch (Exception e) {
            if (isIndexUnusable(e)) releaseIndex();
            return rethrowExceptionAndReturn(e);
        }
    }

    /**
     * Commits any pending documents and closes index structures.
     */
    private void releaseIndex() {
        lock.lock();
        try {
            LOGGER.debug("Rollback and release");
            if (indexer != null) indexer.release();
            indexer = null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Marks the index changed if commit is false or commits the index.
     *
     * @param commit <code>true</code> to commit later, <code>false</code> to commit right away
     */
    private void markIndexChanged(boolean commit) {
        Indexer currentIndexer = getIndexer();
        if (currentIndexer != null) currentIndexer.markIndexChanged(commit);
    }

    /**
     * Commits the index.
     */
    void commitIndex() {
        try {
            Indexer currentIndexer = getIndexer();
            if (currentIndexer != null) currentIndexer.commit();
        } catch (AlreadyClosedException e) {
            LOGGER.debug("Index is closed to commit changes to index", e);
        } catch (Exception e) {
            LOGGER.error("Failed to commit changes to index", e);
        }
    }

    private void initListeners() {
        Collection<IndexListener> discoveredListeners = ClassUtils.resolveProviderInstances(IndexListener.class);
        LOGGER.info("Register {} index listeners", discoveredListeners.size());
        for (IndexListener discoveredListener : discoveredListeners) {
            LOGGER.debug(" - {}", ClassUtils.getName(discoveredListener));
            registerListener(discoveredListener);
        }
    }

    private IndexerOptions createIndexOptions() {
        return (IndexerOptions) IndexerOptions.create(INDEX_NAME).primary(true)
                .tag("application").tag("search")
                .name("Primary").description("The primary index used by the application to provide full text search")
                .build();
    }

    private void initTasks() {
        getThreadPool().scheduleAtFixedRate(new IndexedDocumentsTask(), ofSeconds(10));
        ThreadPool.get().scheduleAtFixedRate(new IndexMaintenanceTask(), ofSeconds(30));
    }

    private void logSettings() {
        LOGGER.info("Index settings: Maximum RAM (Heap)= {} MB, Maximum RAM Buffer Size = {} MB, Maximum RAM Per Thread Buffer Size = {} MB",
                Runtime.getRuntime().maxMemory() / FormatterUtils.M, getRAMBufferSizeMB(), getRAMBPerThreadBufferSizeMB());
    }

    private void initTaskExecutor() {
        threadPool = ThreadPoolFactory.create("Indexer").create();
    }

    private void unlockIndexDirectory(IndexerOptions options) {
        File file = new File(options.directory, "write.lock");
        if (file.exists()) {
            LOGGER.warn("Index directory appears to be locked. Attempting to unlock it: {}", options.getDirectory());
            FileUtils.deleteQuietly(file);
        }
    }

    private Lock getIndexLock(String id) {
        return locks.computeIfAbsent(id, s -> new ReentrantLock());
    }

    class IndexMaintenanceTask implements Runnable {

        private void handleIndex(Indexer indexer) {
            try {
                indexer.commit();
            } catch (Exception e) {
                LOGGER.warn("Failed to commit changes to index {}. root cause: {}", indexer.getId(), getRootCauseDescription(e));
            }
        }

        @Override
        public void run() {
            indexers.values().forEach(this::handleIndex);
        }
    }

    class IndexedDocumentsTask implements Runnable {

        private void processDocuments(Collection<Document> documents) {
            LOGGER.debug("Fire indexed listener for {} documents", documents.size());
            for (IndexListener listener : listeners) {
                try {
                    listener.afterIndexing(documents);
                } catch (Exception e) {
                    LOGGER.atError().setCause(e).log("Failed to process indexed documents with listener {}",
                            ClassUtils.getName(listener));
                }
            }
        }

        @Override
        public void run() {
            while (!pendingDocuments.isEmpty()) {
                Collection<Document> documents = pendingDocuments.poll();
                if (ObjectUtils.isEmpty(documents)) break;
                processDocuments(documents);
            }
        }
    }

    private static class LoggerInfoStream extends InfoStream {

        @Override
        public void message(String component, String message) {
            LOGGER.debug("[Info Stream] {} - {}", component, message);
        }

        @Override
        public boolean isEnabled(String component) {
            return true;
        }

        @Override
        public void close() {
            // no resources to close
        }
    }

}
