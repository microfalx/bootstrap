package net.microfalx.bootstrap.jdbc.support;

import com.zaxxer.hikari.HikariDataSource;
import net.microfalx.database.*;
import net.microfalx.lang.ClassUtils;
import net.microfalx.threadpool.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;
import static net.microfalx.lang.StringUtils.defaultIfEmpty;
import static net.microfalx.lang.StringUtils.toIdentifier;

/**
 * A service which provides database specific support.
 */
@Service
public class DatabaseService implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseService.class);

    @Autowired(required = false)
    private javax.sql.DataSource dataSource;

    @Value("${bootstrap.application.name}")
    private String applicationName;
    private String defaultDataSource;

    /**
     * Returns the task executor for this service.
     *
     * @return a non-null instance
     */
    public ThreadPool getThreadPool() {
        return getDatabaseService().getThreadPool();
    }

    /**
     * Returns registered data sources.
     *
     * @return a non-null instance
     */
    public Collection<DataSource> getDataSources() {
        return getDatabaseService().getDataSources();
    }

    /**
     * Returns a data source by its identifier.
     *
     * @param id the identifier
     * @return the optional data source
     */
    public Optional<DataSource> findDataSource(String id) {
        return getDatabaseService().findDataSource(id);
    }

    /**
     * Returns a data source by its identifier.
     *
     * @param id the identifier
     * @return the data source
     */
    public DataSource getDataSource(String id) {
        return getDatabaseService().getDataSource(id);
    }

    /**
     * Returns registered databases.
     *
     * @return a non-null instance
     */
    public Collection<Database> getDatabases() {
        return getDatabaseService().getDatabases();
    }

    /**
     * Returns the default database.
     *
     * @return the data source
     */
    public Database getDefaultDatabase() {
        return getDatabaseService().getDefaultDatabase();
    }

    /**
     * Returns a database by its identifier.
     *
     * @param id the identifier
     * @return the data source
     */
    public Database getDatabase(String id) {
        return getDatabaseService().getDatabase(id);
    }

    /**
     * Returns a snapshot for each registered databases.
     *
     * @return a non-null instance
     */
    public Collection<Snapshot> getSnapshots() {
        return getDatabaseService().getSnapshots();
    }

    /**
     * Returns a snapshot for a set of databases.
     *
     * @return a non-null instance
     */
    public Collection<Snapshot> getSnapshots(Set<Database> databases) {
        return getDatabaseService().getSnapshots(databases);
    }

    /**
     * Returns a collection of sessions from registered databases.
     *
     * @return a non-null instance
     */
    public Collection<Session> getSessions(boolean sync) {
        return getDatabaseService().getSessions(true);
    }

    /**
     * Returns a map of sessions from registered databases.
     *
     * @return a non-null instance
     */
    public Future<Map<String, Session>> getSessions() {
        return getDatabaseService().getSessions();
    }

    /**
     * Registers a data source to be tracked.
     *
     * @param dataSource the data source
     */
    public void registerDataSource(DataSource dataSource) {
        requireNonNull(dataSource);
        getDatabaseService().registerDataSource(dataSource);
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
        return getDatabaseService().getNodes();
    }

    /**
     * Returns a node by its identifier.
     *
     * @param id the node identifier
     * @return a non-null optional
     */
    public Optional<Node> findNode(String id) {
        requireNotEmpty(id);
        return getDatabaseService().findNode(id);
    }

    /**
     * Returns a database by its identifier.
     *
     * @param id the identifier
     * @return the data source
     */
    public Node getNode(String id) {
        return getDatabaseService().getNode(id);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (dataSource != null) {
            String name = defaultIfEmpty(applicationName, "Default");
            String id = toIdentifier(name);
            this.defaultDataSource = id;
            DataSource newDataSource = updateProperties(DataSource.create(id, name, dataSource)
                    .withDescription("The application database"));
            registerDataSource(newDataSource);
        }
    }

    private net.microfalx.database.DatabaseService getDatabaseService() {
        return net.microfalx.database.DatabaseService.getInstance();
    }

}
