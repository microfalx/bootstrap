package net.microfalx.bootstrap.configuration;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.bootstrap.core.utils.EnvironmentSupport;
import net.microfalx.configuration.AbstractConfigurationSource;
import net.microfalx.lang.annotation.SizeOf;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@SizeOf(shallow = false, deepSize = 100)
public final class EnvironmentConfigurationSource extends AbstractConfigurationSource {

    private final EnvironmentSupport environment;

    public EnvironmentConfigurationSource(Environment environment) {
        this.environment = new EnvironmentSupport(environment);
    }

    @Override
    public String getProperty(String key) {
        return environment.getProperty(key);
    }
}
