package net.microfalx.bootstrap.configuration;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.configuration.AbstractConfigurationSource;
import net.microfalx.lang.annotation.SizeOf;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;

@Component
@Slf4j
@SizeOf(shallow = false, deepSize = 100)
public class EnvironmentConfigurationSource extends AbstractConfigurationSource {

    private final Environment environment;
    private final Binder binder;

    public EnvironmentConfigurationSource(Environment environment) {
        this.environment = environment;
        this.binder = Binder.get(environment);
    }

    @Override
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
