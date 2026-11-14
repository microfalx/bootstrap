package net.microfalx.bootstrap.diagnostics;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.*;
import net.microfalx.argus.diagnostics.Diagnostics;
import net.microfalx.configuration.Configuration;
import net.microfalx.jvm.ObjectSizeEstimator;
import net.microfalx.lang.ClassUtils;
import net.microfalx.threadpool.ThreadPool;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.StringUtils.isEmpty;

/**
 * A service responsible for managing the JVM diagnostics and monitoring.
 */
@Service
@Slf4j
public class DiagnosticsService implements InitializingBean {

    private final Collection<DiagnosticsListener> listeners = new CopyOnWriteArraySet<>();
    private final Set<Diagnostics.Type> collectionsInProgress = ConcurrentHashMap.newKeySet();

    @Autowired private ApplicationContext applicationContext;
    @Autowired private DiagnosticsConfiguration diagnosticsConfiguration;

    /**
     * Returns the last calculated health.
     *
     * @return a non-null instance
     */
    public Health getHealth() {
        return getHealthService().getHealth(Resource.Type.SERVICE);
    }

    /**
     * Returns all diagnostics collected by the application.
     *
     * @return a non-null instance
     */
    public Collection<Diagnostics> getDiagnostics() {
        return getDiagnosticsService().getDiagnostics();
    }

    /**
     * Returns all diagnostics collected between the specified start and end date.
     *
     * @param start the start date
     * @param end   the end date
     * @return a non-null instance
     */
    public Collection<Diagnostics> getDiagnostics(LocalDateTime start, LocalDateTime end) {
        requireNonNull(start);
        requireNonNull(end);
        return getDiagnosticsService().getDiagnostics(start, end);
    }

    /**
     * Returns all diagnostics of a given type, collected between the specified start and end date.
     *
     * @param type  the diagnostics type
     * @param start the start date
     * @param end   the end date
     * @return a non-null instance
     */
    public Collection<Diagnostics> getDiagnostics(Diagnostics.Type type, LocalDateTime start, LocalDateTime end) {
        requireNonNull(type);
        requireNonNull(start);
        requireNonNull(end);
        return getDiagnosticsService().getDiagnostics(type, start, end);
    }

    /**
     * Returns the diagnostics with the given identifier.
     *
     * @param id the identifier of the diagnostics
     * @return the diagnostics, empty if it does not exist
     */
    public Optional<Diagnostics> getDiagnostics(String id) {
        if (isEmpty(id)) return Optional.empty();
        return getDiagnostics().stream().filter(diagnostics -> id.equals(diagnostics.getId())).findFirst();
    }

    /**
     * Collects (asynchronously) a new diagnostics of the given type, with default options.
     * <p>
     * Only one collection of a given type can run at a time.
     *
     * @param type        the diagnostics type
     * @param name        the name of the diagnostics
     * @param description the reason why the diagnostics is collected, can be null
     * @return {@code true} if the collection was scheduled, {@code false} if one of the same type is in progress
     */
    public boolean collect(Diagnostics.Type type, String name, String description) {
        requireNonNull(type);
        requireNonNull(name);
        if (!collectionsInProgress.add(type)) {
            LOGGER.info("Skip diagnostics '{}', a {} is already in progress", name, type);
            return false;
        }
        try {
            Diagnostics diagnostics = getDiagnosticsService().start(type, name, description, null);
            ThreadPool.get().execute(() -> doCollect(type, diagnostics));
            return true;
        } catch (Exception e) {
            collectionsInProgress.remove(type);
            throw new DiagnosticsException("Failed to schedule diagnostics '" + name + "'", e);
        }
    }

    /**
     * Returns whether a diagnostics of the given type is being collected (triggered by this service).
     *
     * @param type the diagnostics type
     * @return {@code true} if in progress, {@code false} otherwise
     */
    public boolean isCollecting(Diagnostics.Type type) {
        requireNonNull(type);
        return collectionsInProgress.contains(type);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initListeners();
        initSettings();
        initHealthContributors();
        initDiagnostics();
        registerObjectSizes();
    }

    private void doCollect(Diagnostics.Type type, Diagnostics diagnostics) {
        LOGGER.info("Collect diagnostics '{}' ({})", diagnostics.getName(), type);
        try {
            diagnostics.collect();
            if (diagnostics.getFailure() != null) {
                LOGGER.warn("Diagnostics '{}' failed: {}", diagnostics.getName(), diagnostics.getFailure());
            } else {
                LOGGER.info("Diagnostics '{}' collected in {}", diagnostics.getName(), diagnostics.getDuration());
            }
        } catch (Exception e) {
            LOGGER.atError().setCause(e).log("Failed to collect diagnostics '{}'", diagnostics.getName());
        } finally {
            collectionsInProgress.remove(type);
        }
    }

    private void initListeners() {
        LOGGER.debug("Initializing diagnostics listeners");
        Collection<DiagnosticsListener> listenersFromContext = applicationContext.getBeansOfType(DiagnosticsListener.class).values();
        for (DiagnosticsListener listener : listenersFromContext) {
            LOGGER.debug("- {}", ClassUtils.getName(listener));
            this.listeners.add(listener);
        }
        LOGGER.debug("Diagnostics listeners initialized, {} listeners", listeners.size());
    }

    private void fireUpdateHealth(Health health) {
        for (DiagnosticsListener listener : listeners) {
            listener.update(health);
        }
    }

    private void initSettings() {
        HealthSettings settings = getHealthService().getSettings()
                .withHealthInterval(diagnosticsConfiguration.getHealthInterval())
                .withScrapeInterval(diagnosticsConfiguration.getScrapeInterval());
        getHealthService().setSettings(settings);
    }

    private void initHealthContributors() {
        getHealthService().register(new DisagnosticsHealthContributor());
        LOGGER.info("Registered diagnostics health contributor, total contributors: {}", HealthService.getInstance().getContributors().size());
    }

    private void initDiagnostics() {
        LOGGER.info("Dump diagnostics to {}", getDiagnosticsService().getResources().toURI());
    }

    private void registerObjectSizes() {
        ObjectSizeEstimator sizeEstimator = ObjectSizeEstimator.get();
        sizeEstimator.registerShallowSizeOfSubclass(net.microfalx.resource.Resource.class, 40);
        sizeEstimator.registerShallowSizeOfSubclass(Configuration.class, 100);
    }

    private HealthService getHealthService() {
        return HealthService.getInstance();
    }

    private net.microfalx.argus.diagnostics.DiagnosticsService getDiagnosticsService() {
        return net.microfalx.argus.diagnostics.DiagnosticsService.getInstance();
    }

    private class DisagnosticsHealthContributor implements HealthContributor {

        @Override
        public String getName() {
            return "Diagnostics";
        }

        @Override
        public boolean supports(Resource.Type type) {
            return type == Resource.Type.SERVICE;
        }

        @Override
        public void update(Health health) {
            fireUpdateHealth(health);
        }

        @Override
        public Collection<Thresholds> getThresholds() {
            return List.of();
        }
    }
}
