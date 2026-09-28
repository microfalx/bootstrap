package net.microfalx.bootstrap.application;

import net.microfalx.lang.JvmUtils;
import net.microfalx.lang.StringUtils;
import net.microfalx.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.util.Map;
import java.util.TimeZone;

import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;
import static net.microfalx.lang.StringUtils.*;

/**
 * A service which provides metadata for a web application.
 */
@Service
public final class ApplicationService implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationService.class);

    @Autowired(required = false) private ApplicationProperties applicationProperties = new ApplicationProperties();

    @Autowired private ConfigurableEnvironment environment;
    private final Application application = new Application();

    /**
     * Returns the application description.
     *
     * @return a non-null instance
     */
    public Application getApplication() {
        return application;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initApplication();
        initTimeZone();
        logApplication();
    }

    @EventListener(ApplicationEnvironmentPreparedEvent.class)
    public void onEnvironmentPrepared(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        registerApplicationProperties(environment);
    }

    @EventListener(ApplicationStartedEvent.class)
    public void onStart(ApplicationStartedEvent event) {
        LOGGER.info("Started application, version: {}, build number: {}, build time: {}",
                application.getVersion(), application.getBuildNumber(), application.getBuildTime());
    }

    private void registerApplicationProperties(ConfigurableEnvironment environment) {
        Map<String, Object> customProperties = Map.of(
                "spring.application.name", application.getId(),
                "spring.application.version", application.getVersion()
        );
        environment.getPropertySources().addFirst(
                new MapPropertySource("applicationProperties", customProperties)
        );
    }

    private void initApplication() {
        application.id = defaultIfEmpty(applicationProperties.getId(), toIdentifier(applicationProperties.getName()));
        application.name = applicationProperties.getName();
        String defaultExecutable = defaultIfEmpty(applicationProperties.getId(), toIdentifier(StringUtils.split(application.name, " ")));
        application.executable = defaultIfEmpty(applicationProperties.getExecutable(), defaultExecutable);
        application.description = applicationProperties.getDescription();
        application.vendor = applicationProperties.getVendor();
        application.url = applicationProperties.getUrl();
        application.version = getVersion();
        application.buildNumber = getBuildNumber();
        application.buildTime = getBuildTime();
        System.setProperty("application.version", application.getVersion());
        System.setProperty("application.build.number", application.getBuildNumber());
        System.setProperty("application.build.time", application.getBuildTime());
        registerApplicationProperties(environment);
    }

    private String getVersion() {
        String version = System.getenv("APP_VERSION");
        if (isNotEmpty(version)) return version;
        version = loadFromHomeFile(".version");
        return defaultIfEmpty(version, defaultIfNull(applicationProperties.getVersion(), "1.0.0"));
    }

    private String getBuildNumber() {
        String buildNumber = System.getenv("APP_BUILD_NUMBER");
        if (isNotEmpty(buildNumber)) return buildNumber;
        buildNumber = loadFromHomeFile(".build-number");
        return defaultIfEmpty(buildNumber, NA_STRING);
    }

    private String getBuildTime() {
        String buildNumber = System.getenv("APP_BUILD_TIME");
        if (isNotEmpty(buildNumber)) return buildNumber;
        buildNumber = loadFromHomeFile(".build-time");
        return defaultIfEmpty(buildNumber, NA_STRING);
    }

    private String loadFromHomeFile(String fileName) {
        Resource resource = Resource.file(new File(JvmUtils.getHomeDirectory(), fileName));
        try {
            if (resource.exists()) return resource.loadAsString();
        } catch (IOException e) {
            // ignore, should not happen
        }
        return null;
    }

    private void initTimeZone() {
        ZoneId systemZoneId = ZoneId.systemDefault();
        ZoneId zoneId = systemZoneId;
        String source = "OS";
        if (isNotEmpty(applicationProperties.getTimeZone())) {
            try {
                zoneId = ZoneId.of(applicationProperties.getTimeZone());
                source = "Application";
            } catch (Exception e) {
                LOGGER.error("Invalid application time zone : {}, root cause: {}", applicationProperties.getTimeZone(), getRootCauseDescription(e));
            }
        }
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        application.timeZone = TimeZone.getTimeZone(zoneId);
        LOGGER.info("Application Time Zone '{}', source '{}', initial time zone '{}'", zoneId, source, systemZoneId);
    }

    private void logApplication() {
        LOGGER.info("Initialize application: {} ({} / {}/ {})", application.getName(), application.getVersion(),
                application.getBuildNumber(), application.getBuildTime());
    }

}
