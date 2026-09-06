package net.microfalx.bootstrap.diagnostics;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.*;
import net.microfalx.configuration.Configuration;
import net.microfalx.jvm.ObjectSizeEstimator;
import net.microfalx.lang.ClassUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A service responsible for managing the JVM diagnostics and monitoring.
 */
@Service
@Slf4j
public class DiagnosticsService implements InitializingBean {

    private final Collection<DiagnosticsListener> listeners = new CopyOnWriteArraySet<>();

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

    @Override
    public void afterPropertiesSet() throws Exception {
        initListeners();
        initSettings();
        initHealthContributors();
        registerObjectSizes();
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
        HealthSettings settings = new HealthSettings()
                .withHealthInterval(diagnosticsConfiguration.getHealthInterval())
                .withScrapeInterval(diagnosticsConfiguration.getScrapeInterval());
        getHealthService().setSettings(settings);
    }

    private void initHealthContributors() {
        getHealthService().register(new DisagnosticsHealthContributor());
        LOGGER.info("Registered diagnostics health contributor, total contributors: {}", HealthService.getInstance().getContributors().size());
    }

    private void registerObjectSizes() {
        ObjectSizeEstimator sizeEstimator = ObjectSizeEstimator.get();
        sizeEstimator.registerShallowSizeOfSubclass(net.microfalx.resource.Resource.class, 40);
        sizeEstimator.registerShallowSizeOfSubclass(Configuration.class, 100);
    }

    private HealthService getHealthService() {
        return HealthService.getInstance();
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
