package net.microfalx.bootstrap.configuration;

import net.microfalx.configuration.ConfigurationService;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.SpringApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import static org.springframework.boot.context.logging.LoggingApplicationListener.DEFAULT_ORDER;

/**
 * A Spring Boot application listener that initializes the configuration service when the
 * application environment is prepared.
 */
public class ConfigurationApplicationListener implements ApplicationListener<SpringApplicationEvent>, Ordered {

    @Override
    public void onApplicationEvent(SpringApplicationEvent event) {
        if (event instanceof ApplicationEnvironmentPreparedEvent environmentPreparedEvent) {
            initializeLoggers(environmentPreparedEvent.getEnvironment());
        }
    }

    @Override
    public int getOrder() {
        return DEFAULT_ORDER + 1;
    }

    private void initializeLoggers(Environment environment) {
        EnvironmentConfigurationSource configurationSource = new EnvironmentConfigurationSource(environment);
        ConfigurationService.getInstance().setSource(configurationSource);
    }
}
