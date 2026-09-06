package net.microfalx.bootstrap.configuration;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.bootstrap.registry.RegistryService;
import net.microfalx.configuration.Configuration;
import net.microfalx.configuration.ConfigurationEvent;
import net.microfalx.configuration.Metadata;
import net.microfalx.registry.Registry;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

@Slf4j
@Service
public class ConfigurationService implements InitializingBean {

    private Configuration configuration;

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private RegistryService registryService;
    @Autowired private EnvironmentConfigurationSource configurationSource;

    private final Collection<ConfigurationListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Returns the registry used to store the configuration.
     *
     * @return a non-null instance
     */
    public Registry getRegistry() {
        return registryService.getRegistry();
    }

    /**
     * Returns the root configuration.
     *
     * @return a non-null instance
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Registers a configuration listener.
     *
     * @param listener a non-null instance
     */
    public void addListener(ConfigurationListener listener) {
        requireNonNull(listener);
        listeners.add(listener);
    }

    /**
     * Returns an application property
     *
     * @param key the name of the property
     * @return the value
     */
    public String getProperty(String key) {
        return configurationSource.getProperty(key);
    }

    /**
     * Returns the root metadata.
     *
     * @return a non-null instance
     */
    public Metadata getRootMetadata() {
        return getConfigurationService().getRootMetadata();
    }

    /**
     * Returns metadata for the given key.
     *
     * @param key the configuration key
     * @return a non-null instance
     */
    public Metadata getMetadata(String key) {
        return getConfigurationService().getMetadata(key);
    }

    /**
     * Returns all registered entries with a given prefix
     *
     * @param prefix the prefix
     * @return a non-null instance
     */
    public Collection<Metadata> getEntries(String prefix) {
        return getConfigurationService().getEntries(prefix);
    }

    /**
     * Registers metadata associated with the configuration entry.
     *
     * @param metadata the metadata to register
     */
    public void registerMetadata(Metadata metadata) {
        getConfigurationService().registerMetadata(metadata);
    }

    /**
     * Notifies listeners that a group (all properties under the group) changed.
     *
     * @param metadata the metadata of the group
     */
    public void notifyGroupChange(Metadata metadata) {
        getConfigurationService().notifyGroupChange(metadata);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initConfiguration();
        initForwardListener();
    }

    /**
     * Clears the caches associated with the configuration.
     */
    public void clearCache() {
        getConfigurationService().clearCache();
    }

    void propertyChanged(Configuration configuration, String key, String previousValue, String currentValue) {
        ConfigurationEvent event = new ConfigurationEvent(configuration, ConfigurationEvent.Type.PROPERTY, key, previousValue, currentValue);
        fireConfigurationEvent(event);
    }

    void fireConfigurationEvent(ConfigurationEvent event) {
        eventPublisher.publishEvent(event);
        for (ConfigurationListener listener : listeners) {
            listener.onEvent(event);
        }
    }

    private void initConfiguration() {
        configuration = getConfigurationService().getConfiguration();
    }

    private void initForwardListener() {
        getConfigurationService().addListener(new ForwardConfigurationListener());
    }

    private net.microfalx.configuration.ConfigurationService getConfigurationService() {
        return net.microfalx.configuration.ConfigurationService.getInstance();
    }

    private class ForwardConfigurationListener implements net.microfalx.configuration.ConfigurationListener {

        @Override
        public void onEvent(ConfigurationEvent event) {
            fireConfigurationEvent(event);
        }
    }

}
