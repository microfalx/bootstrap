package net.microfalx.bootstrap.configuration;

import net.microfalx.configuration.ConfigurationEvent;

/**
 * A listener used to notify services of configuration changes.
 */
public interface ConfigurationListener {

    /**
     * Invoked when the configuration changes.
     *
     * @param event the event
     */
    void onEvent(ConfigurationEvent event);
}
