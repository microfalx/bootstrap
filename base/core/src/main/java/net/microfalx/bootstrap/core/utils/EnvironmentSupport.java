package net.microfalx.bootstrap.core.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.core.env.Environment;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;

/**
 * A utility class that provides support for accessing environment properties.
 */
@Slf4j
public class EnvironmentSupport {

    private final Binder binder;

    public EnvironmentSupport(Environment environment) {
        requireNonNull(environment);
        this.binder = Binder.get(environment);
    }

    public String getProperty(String key) {
        requireNonNull(key);
        try {
            ConfigurationPropertyName name = ConfigurationPropertyName.adapt(key, '.');
            Bindable<String> bindable = Bindable.of(String.class);
            return binder.bindOrCreate(name, bindable, BindHandler.DEFAULT);
        } catch (Exception e) {
            LOGGER.warn("Failed to get the property '{}', root cause: {}", key, getRootCauseDescription(e));
            return null;
        }
    }
}
