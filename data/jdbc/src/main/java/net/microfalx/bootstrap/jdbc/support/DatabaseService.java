package net.microfalx.bootstrap.jdbc.support;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.zaxxer.hikari.HikariDataSource;
import net.microfalx.bootstrap.core.async.ThreadPoolFactory;
import net.microfalx.bootstrap.jdbc.support.mysql.MySqlDatabase;
import net.microfalx.bootstrap.jdbc.support.vertica.VerticaDatabase;
import net.microfalx.bootstrap.store.StoreService;
import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.ConcurrencyUtils;
import net.microfalx.lang.TextUtils;
import net.microfalx.lang.TimeUtils;
import net.microfalx.objectpool.ObjectPoolException;
import net.microfalx.store.api.Store;
import net.microfalx.threadpool.AbstractCallable;
import net.microfalx.threadpool.AbstractRunnable;
import net.microfalx.threadpool.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.unmodifiableCollection;
import static java.util.stream.Collectors.toMap;
import static net.microfalx.bootstrap.jdbc.support.DatabaseUtils.*;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;
import static net.microfalx.lang.ConcurrencyUtils.*;
import static net.microfalx.lang.ExceptionUtils.*;
import static net.microfalx.lang.StringUtils.*;
import static net.microfalx.lang.TimeUtils.millisSince;

/**
 * A service which provides database specific support.
 */
@Service
public class DatabaseService implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseService.class);

    private static final long SESSION_REFRESH_INTERVAL = 5_000;
    private static final long TRANSACTION_REFRESH_INTERVAL = 5_000;
    private static final long STATEMENT_CACHE = 5_000;
    private static final Duration wait = ofSeconds(2);
    private static final Duration timeout = ofSeconds(10);

    private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, Database> databases = new ConcurrentHashMap<>();

    private volatile Map<String, Session> lastSessions = Collections.emptyMap();
    private volatile Map<String, Transaction> lastTransactions = Collections.emptyMap();
    private final Map<String, Lock> databaseLocks = new ConcurrentHashMap<>();
    private final Cache<String, Statement> statements = CacheBuilder.newBuilder().maximumSize(STATEMENT_CACHE).softValues().build();
    private volatile long lastSessionExtractTime = TimeUtils.oneHourAgo();
    private final AtomicBoolean extractingSessions = new AtomicBoolean();
    private volatile long lastTransactionExtractTime = TimeUtils.oneHourAgo();
    private final AtomicBoolean extractingTransactions = new AtomicBoolean();

    @Autowired(required = false)
    private javax.sql.DataSource dataSource;

    private ThreadPool coordinatorTaskExecutor;
    private ThreadPool workerTaskExecutor;

    @Autowired
    private StoreService storeService;

    private Store<Statement, String> statementStore;

    @Value("${bootstrap.application.name}")
    private String applicationName;
    private String defaultDataSource;

    /**
     * Returns the task executor for this service.
     *
     * @return a non-null instance
     */
    public ThreadPool getThreadPool() {
        return coordinatorTaskExecutor;
    }

    /**
     * Returns registered data sources.
     *
     * @return a non-null instance
     */
    public Collection<DataSource> getDataSources() {
        return unmodifiableCollection(dataSources.values());
    }

    /**
     * Returns a data source by its identifier.
     *
     * @param id the identifier
     * @return the optional data source
     */
    public Optional<DataSource> findDataSource(String id) {
        requireNotEmpty(id);
        return Optional.ofNullable(dataSources.get(toIdentifier(id)));
    }

    /**
     * Returns a data source by its identifier.
     *
     * @param id the identifier
     * @return the data source
     */
    public DataSource getDataSource(String id) {
        requireNotEmpty(id);
        DataSource dataSource = dataSources.get(toIdentifier(id));
        if (dataSource == null) {
            throw new IllegalArgumentException("A data source with identifier '" + id + "' is not registered");
        }
        return dataSource;
    }

    /**
     * Returns registered databases.
     *
     * @return a non-null instance
     */
    public Collection<Database> getDatabases() {
        return unmodifiableCollection(databases.values());
    }

    /**
     * Returns the default database.
     *
     * @return the data source
     */
    public Database getDefaultDatabase() {
        if (defaultDataSource == null) {
            throw new IllegalStateException("No default data source is configured");
        }
        return getDatabase(defaultDataSource);
    }

    /**
     * Returns a database by its identifier.
     *
     * @param id the identifier
     * @return the data source
     */
    public Database getDatabase(String id) {
        requireNotEmpty(id);
        id = toIdentifier(id);
        Database database = databases.get(id);
        if (database == null) {
            throw new DatabaseNotFoundException("A database with identifier '" + id + "' is not registered");
        }
        return database;
    }

    /**
     * Returns a snapshot for each registered databases.
     *
     * @return a non-null instance
     */
    public Collection<Snapshot> getSnapshots() {
        return getSnapshots(new HashSet<>(databases.values()));
    }

    /**
     * Returns a snapshot for a set of databases.
     *
     * @return a non-null instance
     */
    public Collection<Snapshot> getSnapshots(Set<Database> databases) {
        requireNotEmpty(databases);
        Collection<Snapshot> snapshots = new ArrayList<>();
        Collection<Future<Snapshot>> futures = new ArrayList<>();
        for (Database database : databases) {
            Snapshot snapshot = new Snapshot(database);
            snapshots.add(snapshot);
            futures.add(coordinatorTaskExecutor.submit(new ExtractSnapshot(snapshot)));
        }
        int pendingTasks = METRICS.getTimer("Get Snapshots").record(() -> waitForFutures(futures, timeout));
        if (pendingTasks > 0) LOGGER.debug("Incomplete list of snapshots, pending tasks: {}", pendingTasks);
        return snapshots;

    }

    /**
     * Returns a collection of sessions from registered databases.
     *
     * @return a non-null instance
     */
    public Collection<Session> getSessions(boolean sync) {
        if ((this.lastSessions.isEmpty() || millisSince(lastSessionExtractTime) > SESSION_REFRESH_INTERVAL)
                && extractingSessions.compareAndSet(false, true)) {
            getSessions();
        }
        if (sync) waitForCondition(this.extractingSessions, true, wait);
        return this.lastSessions.values();
    }

    /**
     * Returns a map of sessions from registered databases.
     *
     * @return a non-null instance
     */
    public Future<Map<String, Session>> getSessions() {
        return coordinatorTaskExecutor.submit(METRICS.getTimer("Get Sessions").wrap(new ExtractSessions()));
    }

    /**
     * Returns a collection of sessions from registered databases.
     *
     * @return a non-null instance
     */
    public Collection<Transaction> getTransactions(boolean sync) {
        if ((this.lastTransactions.isEmpty() || millisSince(lastTransactionExtractTime) > TRANSACTION_REFRESH_INTERVAL)
                && extractingTransactions.compareAndSet(false, true)) {
            getTransactions();
        }
        if (sync) waitForCondition(this.extractingTransactions, true, wait);
        return this.lastTransactions.values();
    }

    /**
     * Returns a collection of transactions from registered databases.
     *
     * @return a non-null instance
     */
    public Future<Map<String, Transaction>> getTransactions() {
        return coordinatorTaskExecutor.submit(METRICS.getTimer("Get Transactions").wrap(new ExtractTransactions()));
    }

    /**
     * Returns a collection of transactions from registered databases.
     *
     * @return a non-null instance
     */
    public Collection<Statement> getStatements(LocalDateTime start, LocalDateTime end) {
        Callable<Collection<Statement>> callable = METRICS.getTimer("Get Statements").wrap(new ExtractStatements(start, end));
        try {
            return callable.call();
        } catch (Exception e) {
            return rethrowExceptionAndReturn(e);
        }
    }

    /**
     * Returns a session by its identifier.
     *
     * @param id the session identifier
     * @return a non-null optional
     */
    public Optional<Session> findSession(String id) {
        requireNotEmpty(id);
        return Optional.ofNullable(lastSessions.get(id));
    }

    /**
     * Returns a transaction by its identifier.
     *
     * @param id the session identifier
     * @return a non-null optional
     */
    public Optional<Transaction> findTransaction(String id) {
        requireNotEmpty(id);
        return Optional.ofNullable(lastTransactions.get(id));
    }

    /**
     * Registers a data source to be tracked.
     *
     * @param dataSource the data source
     */
    public void registerDataSource(DataSource dataSource) {
        requireNonNull(dataSource);
        String id = toIdentifier(dataSource.getId());
        if (!dataSources.containsKey(id)) {
            LOGGER.info("Register data source '{}', name '{}'", dataSource.getId(), dataSource.getName());
        } else {
            LOGGER.info("Reload data source '{}', name '{}'", dataSource.getId(), dataSource.getName());
        }
        releaseDataSource(dataSource);
        if (!dataSource.isNode()) {
            Database prevDatabase = databases.get(id);
            if (prevDatabase != null) workerTaskExecutor.submit(new CloseDatabase(prevDatabase));
            Database database = createDatabase(dataSource);
            if (database != null) {
                databases.put(id, database);
                workerTaskExecutor.submit(new ValidateDatabase(database));
            }
        }
        dataSources.put(id, dataSource);
    }

    /**
     * Closes and releases a data source.
     *
     * @param dataSource the data source
     */
    void releaseDataSource(DataSource dataSource) {
        requireNonNull(dataSource);
        String id = toIdentifier(dataSource.getId());
        DataSource prevDataSource = dataSources.remove(id);
        if (prevDataSource != null && !prevDataSource.getId().equals(defaultDataSource)) {
            workerTaskExecutor.submit(new CloseDataSource(prevDataSource));
        }
    }

    /**
     * Returns a statement by its identifier.
     *
     * @param id the statement identifier
     * @return an optional
     */
    public Optional<Statement> getStatement(String id) {
        requireNonNull(id);
        Statement statement = statements.getIfPresent(id);
        if (statement != null) return Optional.of(statement);
        statement = statementStore.find(id);
        if (statement != null) statements.put(id, statement);
        return Optional.ofNullable(statement);
    }

    /**
     * Registers a new statement.
     *
     * @param statement the SQL statement
     * @return a statement
     */
    public Statement registerStatement(Statement statement) {
        requireNonNull(statement);
        Statement prevStatement = statements.getIfPresent(statement.getId());
        if (prevStatement == null) {
            statementStore.add(statement);
            statements.put(statement.getId(), statement);
        }
        return prevStatement != null ? prevStatement : statement;
    }

    /**
     * Updates the data source properties with information
     *
     * @param dataSource the data source
     * @return an update data source
     */
    public DataSource updateProperties(DataSource dataSource) {
        javax.sql.DataSource unwrapped = dataSource.unwrap();
        if (unwrapped instanceof HikariDataSource hikariDataSource) {
            dataSource = dataSource.withUri(URI.create(hikariDataSource.getJdbcUrl())).withUserName(hikariDataSource.getUsername());
        } else {
            LOGGER.error("Unknown data source implementation: {}", ClassUtils.getName(dataSource));
        }
        return dataSource;
    }

    /**
     * Returns nodes from all databases.
     *
     * @return a non-null instance
     */
    public Collection<Node> getNodes() {
        return databases.values().stream().flatMap(database -> database.getNodes().stream()).toList();
    }

    /**
     * Returns a node by its identifier.
     *
     * @param id the node identifier
     * @return a non-null optional
     */
    public Optional<Node> findNode(String id) {
        requireNotEmpty(id);
        for (Database database : databases.values()) {
            Optional<Node> node = database.getNode(id);
            if (node.isPresent()) return node;
        }
        return Optional.empty();
    }

    /**
     * Returns a database by its identifier.
     *
     * @param id the identifier
     * @return the data source
     */
    public Node getNode(String id) {
        requireNotEmpty(id);
        String finalId = toIdentifier(id);
        return databases.values().stream().filter(database -> database.getId().equals(finalId))
                .findFirst().orElseThrow(() -> new DatabaseNotFoundException("A database node with identifier '" + id + "' is not registered"));
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initializeExecutor();
        if (dataSource != null) {
            String name = defaultIfEmpty(applicationName, "Default");
            String id = toIdentifier(name);
            this.defaultDataSource = id;
            DataSource newDataSource = updateProperties(DataSource.create(id, name, dataSource)
                    .withDescription("The application database"));
            registerDataSource(newDataSource);
        }
        statementStore = storeService.register(Store.Options.create(joinNames("Database","Statement")));
        coordinatorTaskExecutor.scheduleWithFixedDelay(new ValidateDatabases(), AVAILABILITY_INTERVAL.dividedBy(2));
    }

    private void initializeExecutor() {
        coordinatorTaskExecutor = ThreadPoolFactory.create("DbC").setRatio(2).create();
        workerTaskExecutor = ThreadPoolFactory.create("DbW").setRatio(3).create();
    }

    private Database createDatabase(DataSource dataSource) {
        URI uri = DatabaseUtils.getURI(dataSource);
        if (uri == null) return null;
        String scheme = uri.getScheme();
        if (MYSQL_SCHEME.equals(scheme) || MARIADB_SCHEME.equals(scheme)) {
            return new MySqlDatabase(this, dataSource.getId(), dataSource.getName(), dataSource);
        } else if (VERTICA_SCHEME.equals(scheme)) {
            return new VerticaDatabase(this, dataSource.getId(), dataSource.getName(), dataSource);
        } else {
            LOGGER.error("Unknown JDBC scheme: {}", scheme);
            return null;
        }
    }

    private void registerStatements(Collection<? extends StatementAware> entries) {
        for (StatementAware entry : entries) {
            if (entry.getStatement() == null) continue;
            try {
                registerStatement(entry.getStatement());
            } catch (Exception e) {
                LOGGER.atError().setCause(e).log("Failed to analyze statement {}",
                        TextUtils.abbreviate(entry.getStatement().getContent(), 80));
            }
        }
    }

    private Lock getDatabaseLock(Database database) {
        return databaseLocks.computeIfAbsent(database.getId(), s -> new ReentrantLock());
    }

    private void registerAvailability(Node node) {
        requireNonNull(node);
        if (node instanceof AbstractNode abstractNode) {
            abstractNode.setState(Node.State.UP);
        }
    }

    private void registerFailure(Node node, Throwable throwable) {
        requireNonNull(node);
        if (node instanceof AbstractNode abstractNode) {
            abstractNode.setState(Node.State.DOWN, getRootCauseDescription(throwable));
        }
    }

    class ExtractSnapshot extends AbstractCallable<Snapshot> {

        private final Snapshot snapshot;

        ExtractSnapshot(Snapshot snapshot) {
            this.snapshot = snapshot;
            setName(joinNames("Extract Snapshot", snapshot.getDatabase().getName()));
        }

        @Override
        public Snapshot call() throws Exception {
            snapshot.setNodes(snapshot.getDatabase().getNodes());
            Future<Collection<Session>> sessions = workerTaskExecutor.submit(new ExtractSessionsForDatabase(snapshot.getDatabase()));
            Future<Collection<Transaction>> transactions = workerTaskExecutor.submit(new ExtractTransactionsForDatabase(snapshot.getDatabase()));
            Collection<Future<?>> futures = Arrays.asList(sessions, transactions);
            snapshot.setIncomplete(waitForFutures(futures, timeout) > 0);
            snapshot.setSessions(ConcurrencyUtils.getResult(sessions));
            snapshot.setTransactions(ConcurrencyUtils.getResult(transactions));
            return snapshot;
        }
    }

    static class CloseDataSource extends AbstractRunnable {

        private final DataSource dataSource;

        public CloseDataSource(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public void run() {
            try {
                dataSource.close();
            } catch (Exception e) {
                LOGGER.warn("Failed to close data source '{}', root cause: {}", describe(dataSource), getRootCauseName(e));
            }
        }
    }

    static class CloseDatabase extends AbstractRunnable {

        private final Database database;

        public CloseDatabase(Database database) {
            this.database = database;
        }

        @Override
        public void run() {
            try {
                ((AbstractDatabase) database).close();
            } catch (Exception e) {
                LOGGER.warn("Failed to close database '{}', root cause: {}", describe(database), getRootCauseName(e));
            }
        }
    }

    static class ValidateDatabase extends AbstractRunnable {

        private final Database database;

        public ValidateDatabase(Database database) {
            this.database = database;
            setName(joinNames("Validate", database.getName()));
        }

        @Override
        public void run() {
            try {
                database.validate();
            } catch (Exception e) {
                LOGGER.warn("Failed to validate database '{}', root cause: {}", describe(database), getRootCauseName(e));
            }
        }
    }

    class ValidateDatabases extends AbstractRunnable {

        public ValidateDatabases() {
            setName(joinNames("Validate", "Databases"));
        }

        @Override
        public void run() {
            for (Database database : databases.values()) {
                new ValidateDatabase(database).run();
            }
        }
    }

    class ExtractSessions extends AbstractCallable<Map<String, Session>> {

        public ExtractSessions() {
            setName("Extract Sessions");
        }

        @Override
        public Map<String, Session> call() throws Exception {
            lastSessionExtractTime = currentTimeMillis();
            try {
                Map<String, Session> sessions = new HashMap<>();
                Collection<Future<Collection<Session>>> futures = new ArrayList<>();
                for (Database database : databases.values()) {
                    futures.add(workerTaskExecutor.submit(new ExtractSessionsForDatabase(database)));
                }
                int pendingTasks = waitForFutures(futures, timeout);
                if (pendingTasks > 0) LOGGER.debug("Incomplete list of sessions, pending tasks: {}", pendingTasks);
                sessions.putAll(collectFutures(futures).stream().flatMap(Collection::stream)
                        .collect(toMap(Session::getId, session -> session)));
                DatabaseService.this.lastSessions = sessions;
                return sessions;
            } finally {
                extractingSessions.set(false);
            }
        }
    }

    class ExtractTransactions extends AbstractCallable<Map<String, Transaction>> {

        public ExtractTransactions() {
            setName("Extract Transactions");
        }

        @Override
        public Map<String, Transaction> call() throws Exception {
            lastTransactionExtractTime = currentTimeMillis();
            try {
                Map<String, Transaction> transactions = new HashMap<>();
                Collection<Future<Collection<Transaction>>> futures = new ArrayList<>();
                for (Database database : databases.values()) {
                    futures.add(workerTaskExecutor.submit(new ExtractTransactionsForDatabase(database)));
                }
                int pendingTasks = waitForFutures(futures, timeout);
                if (pendingTasks > 0) LOGGER.debug("Incomplete list of transactions, pending tasks: {}", pendingTasks);
                transactions.putAll(collectFutures(futures).stream().flatMap(Collection::stream)
                        .collect(toMap(Transaction::getId, transaction -> transaction)));
                DatabaseService.this.lastTransactions = transactions;
                return transactions;
            } finally {
                extractingTransactions.set(false);
            }
        }
    }

    class ExtractStatements extends AbstractCallable<Collection<Statement>> {

        private final LocalDateTime start;
        private final LocalDateTime end;

        public ExtractStatements(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
            setName("Extract Statements");
        }

        @Override
        public Collection<Statement> call() throws Exception {
            Collection<Future<Collection<Statement>>> futures = new ArrayList<>();
            for (Database database : databases.values()) {
                futures.add(workerTaskExecutor.submit(new ExtractStatementsForDatabase(database, start, end)));
            }
            int pendingTasks = waitForFutures(futures, timeout.multipliedBy(5));
            if (pendingTasks > 0) LOGGER.debug("Incomplete list of statements, pending tasks: {}", pendingTasks);
            return collectFutures(futures).stream().flatMap(Collection::stream).toList();
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", ExtractStatements.class.getSimpleName() + "[", "]")
                    .add("start=" + start)
                    .add("end=" + end)
                    .toString();
        }
    }

    class ExtractSessionsForDatabase extends AbstractCallable<Collection<Session>> {

        private final Database database;

        public ExtractSessionsForDatabase(Database database) {
            this.database = database;
            setName(joinNames("Extract Sessions", database.getName()));
        }

        @Override
        public Collection<Session> call() throws Exception {
            return ConcurrencyUtils.withTryLock(getDatabaseLock(database), () -> {
                Collection<Session> sessions = Collections.emptyList();
                try {
                    sessions = database.getSessions();
                    registerAvailability(database);
                } catch (ObjectPoolException e) {
                    registerFailure(database, e);
                } catch (Exception e) {
                    registerFailure(database, e);
                    LOGGER.atError().setCause(e).log("Failed to extract sessions from {}", describe(database));
                }
                registerStatements(sessions);
                return sessions;
            }, timeout).orElse(Collections.emptyList());
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", ExtractSessionsForDatabase.class.getSimpleName() + "[", "]")
                    .add("database=" + describe(database))
                    .toString();
        }
    }

    class ExtractTransactionsForDatabase extends AbstractCallable<Collection<Transaction>> {

        private final Database database;

        public ExtractTransactionsForDatabase(Database database) {
            this.database = database;
            setName(joinNames("Extract Transactions", database.getName()));
        }

        @Override
        public Collection<Transaction> call() throws Exception {
            return ConcurrencyUtils.withTryLock(getDatabaseLock(database), () -> {
                Collection<Transaction> transactions = Collections.emptyList();
                try {
                    transactions = database.getTransactions();
                    registerAvailability(database);
                } catch (ObjectPoolException e) {
                    registerFailure(database, e);
                } catch (Exception e) {
                    registerFailure(database, e);
                    LOGGER.atError().setCause(e).log("Failed to extract transactions from {}", describe(database));
                    transactions = Collections.emptyList();
                }
                registerStatements(transactions);
                return transactions;
            }, timeout).orElse(Collections.emptyList());
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", ExtractTransactionsForDatabase.class.getSimpleName() + "[", "]")
                    .add("database=" + describe(database))
                    .toString();
        }
    }

    class ExtractStatementsForDatabase extends AbstractCallable<Collection<Statement>> {

        private final LocalDateTime start;
        private final LocalDateTime end;
        private final Database database;

        public ExtractStatementsForDatabase(Database database, LocalDateTime start, LocalDateTime end) {
            this.database = database;
            this.start = start;
            this.end = end;
            setName(joinNames("Extract Statements", database.getName()));
        }

        @Override
        public Collection<Statement> call() throws Exception {
            try {
                return database.getStatements(start, end);
            } catch (ObjectPoolException e) {
                registerFailure(database, e);
            } catch (Exception e) {
                LOGGER.atError().setCause(e).log("Failed to extract statements from {}", describe(database));
            }
            return Collections.emptyList();
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", ExtractStatementsForDatabase.class.getSimpleName() + "[", "]")
                    .add("start=" + start)
                    .add("end=" + end)
                    .add("database=" + describe(database))
                    .toString();
        }
    }
}
