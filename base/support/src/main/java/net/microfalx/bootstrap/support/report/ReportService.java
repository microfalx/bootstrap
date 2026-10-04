package net.microfalx.bootstrap.support.report;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.HealthService;
import net.microfalx.argus.api.HealthSettings;
import net.microfalx.argus.api.Issue;
import net.microfalx.argus.report.Fragment;
import net.microfalx.argus.report.Report;
import net.microfalx.bootstrap.application.Application;
import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.ObjectUtils;
import net.microfalx.lang.StringUtils;
import net.microfalx.threadpool.ThreadPool;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.time.Duration.ofHours;
import static java.util.Collections.unmodifiableCollection;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;

@Service
@Slf4j
public class ReportService implements InitializingBean {

    @Autowired private Application application;
    @Autowired private ReportConfiguration configuration;
    @Autowired private ThreadPool threadPool;
    @Autowired private ApplicationContext applicationContext;

    private final Collection<ReportingListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Returns the configuration controlling the reporting service.
     *
     * @return a non-null instance
     */
    public ReportConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Returns whether the given token is valid.
     * <p>
     * If the reporting service is not configured with a secret token, this method will always return {@code true}.
     *
     * @param token the key to be validated
     * @return {@code true} if the key is valid, {@code false} otherwise
     */
    public boolean isValid(String token) {
        if (StringUtils.isEmpty(configuration.getReportToken())) return true;
        return ObjectUtils.equals(configuration.getReportToken(), token);
    }

    /**
     * Returns registered providers.
     *
     * @return a non-null instance
     */
    public Collection<Fragment.Provider> getProviders() {
        return unmodifiableCollection(getReportService().getProviders());
    }

    /**
     * Returns registered issues.
     *
     * @return a non-null instance
     */
    public Collection<Issue> getIssues() {
        return getReportService().getIssues();

    }

    /**
     * Returns the number of issues with the given severity or higher.
     *
     * @param severity the severity
     * @return the number of issues
     */
    public int getIssueCount(Issue.Severity severity) {
        return getReportService().getIssueCount(severity);
    }

    /**
     * Creates a report by aggregating all fragments from the providers.
     *
     * @return a non-null instance
     */
    public Report createReport() {
        return getReportService().createReport();
    }

    /**
     * Sends a report about the system, using the given duration as the report time interval.
     *
     * @param interval the reporting interval
     */
    public void send(Duration interval) {
        send(interval, null);
    }

    /**
     * Sends a report about the system, using the given duration as the report time interval.
     *
     * @param interval the reporting interval
     * @param suffix   a suffix added to the title, to signal a special case
     */
    public void send(Duration interval, String suffix) {
        requireNonNull(interval);
        getReportService().send(interval, suffix);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        loadListeners();
        reload(true);
        initListeners();
        initApplicationContext();
        initSettings();
    }

    @EventListener
    public void onApplicationEvent(ApplicationStartedEvent event) {
        initApplicationContext();
        if (configuration.isEnabled() && configuration.isOnBoot()) {
            threadPool.execute(() -> send(ofHours(1), "Startup"));
        }
    }

    private void loadListeners() {
        LOGGER.debug("Loading reporting listeners");
        Collection<ReportingListener> loadedListeners = ClassUtils.resolveProviderInstances(ReportingListener.class);
        for (ReportingListener loadedProvider : loadedListeners) {
            LOGGER.debug(" - {}", ClassUtils.getName(loadedProvider));
            if (loadedProvider instanceof ApplicationContextAware applicationContextAware) {
                applicationContextAware.setApplicationContext(applicationContext);
            }
            listeners.add(loadedProvider);
        }
        LOGGER.info("Loaded {} reporting listeners", listeners.size());
    }

    private void reload(boolean onStartup) {
        initConfiguration();
    }

    private void initListeners() {
        configuration.addListener(event -> {
            LOGGER.info("Settings changed for group '{}', reload", event.getKey());
            reload(false);
        });
    }

    private void initConfiguration() {

    }


    private net.microfalx.argus.report.ReportService getReportService() {
        return net.microfalx.argus.report.ReportService.getInstance();
    }

    private void initApplicationContext() {
        getReportService().getProviders().forEach(provider -> {
            if (provider instanceof ApplicationContextAware applicationContextAware) {
                applicationContextAware.setApplicationContext(applicationContext);
            }
        });
    }

    private void initSettings() {
        HealthSettings settings = getHealthService().getSettings()
                .withReportToken(configuration.getReportToken());
        getHealthService().setSettings(settings);
    }

    private HealthService getHealthService() {
        return HealthService.getInstance();
    }

}
