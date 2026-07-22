package net.microfalx.bootstrap.metrics;

import lombok.CustomLog;
import net.microfalx.bootstrap.core.utils.ApplicationContextSupport;
import net.microfalx.bootstrap.resource.ResourceService;
import net.microfalx.jvm.ServerMetrics;
import net.microfalx.jvm.VirtualMachineMetrics;
import net.microfalx.lang.ClassUtils;
import net.microfalx.metrics.Query;
import net.microfalx.metrics.Repository;
import net.microfalx.metrics.Result;
import net.microfalx.threadpool.ThreadPool;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Collections.unmodifiableCollection;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;

@Service
@CustomLog
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MetricsService extends ApplicationContextSupport implements InitializingBean {

    private final Collection<Repository> repositories = new CopyOnWriteArrayList<>();

    // Do not remove, tt is wired because resource service setups the JVM paths
    @Autowired(required = false) private ResourceService resourceService;

    private ThreadPool threadPool;

    /**
     * Returns the thread pool used to collect metrics.
     *
     * @return a non-null instance
     */
    public ThreadPool getThreadPool() {
        if (threadPool == null) {
            threadPool = ThreadPool.builder("Metrics").maximumSize(5).build();
        }
        return threadPool;
    }

    /**
     * Returns registered repositories.
     *
     * @return a non-null instance
     */
    public Collection<Repository> getRepositories() {
        return unmodifiableCollection(repositories);
    }

    /**
     * Executes a query with a repository and returns the results.
     * <p>
     * Each query targets a specific repository using {@link net.microfalx.metrics.Query#getType()}. If no repository can be located, the
     * query will fail.
     *
     * @param query the query
     * @return a non-null instance
     * @see Repository
     */
    public Result query(Query query) {
        Repository repository = locateRepository(query);
        return repository.query(query);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initializeRepositories();
        initializeMetricsCollectors();
    }

    private Repository locateRepository(Query query) {
        requireNonNull(query);
        for (Repository repository : repositories) {
            if (repository.supports(query)) return repository;
        }
        throw new MetricException("A repository for query type '" + query.getType() + "' is not registered, query " + query);
    }

    private void initializeRepositories() {
        LOGGER.debug("Loads metrics repositories:");
        Collection<Repository> loadedRepositories = ClassUtils.resolveProviderInstances(Repository.class);
        for (Repository loadedRepository : loadedRepositories) {
            LOGGER.debug(" - {}", ClassUtils.getName(loadedRepository));
            if (loadedRepository instanceof ApplicationContextSupport applicationContextSupport) {
                applicationContextSupport.update(this);
            }
            repositories.add(loadedRepository);
        }
        LOGGER.info("Loaded {} metric repositories", repositories.size());
    }

    private void initializeMetricsCollectors() {
        LOGGER.debug("Initialize metrics collectors");
        ThreadPool threadPool = getThreadPool();
        try {
            VirtualMachineMetrics.get().useDisk("jvm").setExecutor(threadPool).start();
        } catch (Exception e) {
            LOGGER.error("Failed to start JVM metrics", e);
        }
        try {
            ServerMetrics.get().useDisk("server").setExecutor(threadPool).start();
        } catch (Exception e) {
            LOGGER.error("Failed to start server metrics", e);
        }
    }
}
