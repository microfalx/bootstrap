package net.microfalx.bootstrap.logger;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.Alert;
import net.microfalx.argus.api.LoggerSettings;
import net.microfalx.bootstrap.core.utils.ApplicationContextSupport;
import net.microfalx.bootstrap.store.StoreService;
import net.microfalx.lang.EnumUtils;
import net.microfalx.lang.TextUtils;
import net.microfalx.lang.service.ServiceLocator;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;

@Slf4j
@Service
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggerService extends ApplicationContextSupport implements InitializingBean {

    @Autowired private LoggerProperties properties;
    @Autowired private StoreService storeService;

    @Override
    public void afterPropertiesSet() throws Exception {
        initSettings();
        dumpLogger();
    }

    /**
     * Returns the application alerts for a given time interval.
     *
     * @param start the start time
     * @param end   the end time
     * @return a non-null instance
     */
    public Collection<Alert> getAlerts(LocalDateTime start, LocalDateTime end) {
        return getLoggerService().getAlerts(start, end);
    }

    /**
     * Returns an alert by its identifier.
     *
     * @param id the alert identifier
     * @return the alert, null if it does not exist
     */
    public Alert getAlert(String id) {
        return getLoggerService().getAlert(id);
    }

    /**
     * Clears all alerts.
     */
    public long clearAlerts() {
        return getLoggerService().clear();
    }

    /**
     * Acknowledge pending alerts.
     */
    public long acknowledgeAlerts() {
        return getLoggerService().acknowledge();
    }

    private void initSettings() {
        LoggerSettings.Protocol protocol = EnumUtils.fromName(LoggerSettings.Protocol.class, properties.getGelf().getProtocol().name(), LoggerSettings.Protocol.UDP);
        LoggerSettings.Gelf gelf = new LoggerSettings.Gelf()
                .withHostname(properties.getGelf().getHostname())
                .withPort(properties.getGelf().getPort())
                .withFacility(properties.getGelf().getFacility())
                .withProtocol(protocol)
                .withOnlyAlerts(properties.getGelf().isOnlyAlerts());
        protocol = EnumUtils.fromName(LoggerSettings.Protocol.class, properties.getSyslog().getProtocol().name(), LoggerSettings.Protocol.UDP);
        LoggerSettings.Syslog syslog = new LoggerSettings.Syslog()
                .withHostname(properties.getSyslog().getHostname())
                .withPort(properties.getSyslog().getPort())
                .withFacility(properties.getSyslog().getFacility())
                .withProtocol(protocol)
                .withOnlyAlerts(properties.getSyslog().isOnlyAlerts());
        LoggerSettings settings = new LoggerSettings()
                .withApplication(properties.getApplication())
                .withProcess(properties.getProcess())
                .withDebug(properties.isDebug())
                .withDirectory(properties.getDirectory())
                .withFileCount(properties.getFileCount())
                .withFileSize(properties.getFileSize())
                .withTrace(properties.isTrace())
                .withGelf(gelf)
                .withSyslog(syslog);
        getLoggerService().setSettings(settings);
    }

    private net.microfalx.argus.api.LoggerService getLoggerService() {
        return net.microfalx.argus.api.LoggerService.getInstance();
    }

    private void dumpLogger() {
        String log = ServiceLocator.getLog();
        LOGGER.debug("Logger storage is at {}", storeService.getDirectory());
        LOGGER.info("Pre-initialization logs:\n{}", TextUtils.insertSpacesWithBlock(log, 10));
        // disable quiet mode, so that the logger service can log to the console
        ServiceLocator.setQuiet(false);
    }
}
