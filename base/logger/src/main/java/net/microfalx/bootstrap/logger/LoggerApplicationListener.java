package net.microfalx.bootstrap.logger;

import net.microfalx.argus.api.LoggerService;
import net.microfalx.argus.api.LoggerSettings;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.SpringApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import static org.springframework.boot.context.logging.LoggingApplicationListener.DEFAULT_ORDER;

public class LoggerApplicationListener implements ApplicationListener<SpringApplicationEvent>, Ordered {

    @Override
    public void onApplicationEvent(SpringApplicationEvent event) {
        if (event instanceof ApplicationEnvironmentPreparedEvent environmentPreparedEvent) {
            initializeLoggers(environmentPreparedEvent.getEnvironment());
        }
    }

    @Override
    public int getOrder() {
        return DEFAULT_ORDER + 2;
    }

    private void initializeLoggers(Environment environment) {
        System.setProperty(LoggerSettings.DIRECTORY_PROP, environment.getProperty("bootstrap.logger.directory"));
        LoggerService.getInstance().register();
    }
}
