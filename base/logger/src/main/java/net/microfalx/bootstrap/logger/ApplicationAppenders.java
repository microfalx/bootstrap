package net.microfalx.bootstrap.logger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.LevelFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.util.Duration;
import ch.qos.logback.core.util.FileSize;
import net.microfalx.lang.FileUtils;
import net.microfalx.lang.JvmUtils;
import net.microfalx.lang.ObjectUtils;
import net.microfalx.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static net.microfalx.bootstrap.logger.LoggerUtils.getLoggerContext;
import static net.microfalx.bootstrap.logger.LoggerUtils.getRootLogger;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;
import static net.microfalx.lang.FileUtils.isDirectoryWritable;
import static net.microfalx.lang.FormatterUtils.formatBytes;
import static net.microfalx.lang.IOUtils.*;
import static net.microfalx.lang.StringUtils.isEmpty;
import static net.microfalx.lang.StringUtils.isNotEmpty;

/**
 * A collection of appenders used to log everything to files.
 */
class ApplicationAppenders {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationAppenders.class);

    private final Environment environment;
    private File logsDirectory;

    public ApplicationAppenders(Environment environment) {
        requireNonNull(environment);
        this.environment = environment;
    }

    boolean hasLogsDirectory() {
        File configuredLogsDirectory = getConfiguredLogsDirectory();
        return JvmUtils.hasLogsDirectory() || configuredLogsDirectory != null;
    }

    File getLogsDirectory() {
        if (logsDirectory == null) {
            File configuredLogsDirectory = getConfiguredLogsDirectory();
            logsDirectory = ObjectUtils.defaultIfNull(configuredLogsDirectory, JvmUtils.getLogsDirectory());
        }
        return logsDirectory;
    }

    void move(Resource resource) {
        File[] files = getLogsDirectory().listFiles(this::acceptArchives);
        if (files != null) {
            for (File file : files) {
                try {
                    Resource source = Resource.file(file);
                    Resource destination = resource.resolve(file.getName());
                    destination.copyFrom(source);
                    source.delete();
                } catch (IOException e) {
                    LOGGER.warn("Failed to move log archive file: {}, root cause: {}", file.getAbsolutePath(), getRootCauseDescription(e));
                }
            }
        }
    }

    void register() {
        try {
            if (hasLogsDirectory()) {
                archiveLogs();
                addStandardAppenders();
                addRegisteredAppenders();
            }
        } catch (Exception e) {
            LOGGER.atError().setCause(e).log("Error registering logger appenders");
        }
    }

    private void addRegisteredAppenders() {
        LoggerLoader loader = new LoggerLoader();
        loader.load();
        LOGGER.debug("Loaded {}", loader.getAppenders().size() + " from descriptors");
        LoggerContext loggerContext = getLoggerContext();
        for (net.microfalx.bootstrap.logger.Appender appender : loader.getAppenders()) {
            FileAppender<ILoggingEvent> fileAppender = createAppender(appender, loggerContext);
            for (String included : appender.getIncluded()) {
                loggerContext.getLogger(included).addAppender(fileAppender);
            }
        }
    }

    private void addStandardAppenders() {
        // log everything in one file, easier to track
        registerAllAppender();
        // log all warnings in one file
        net.microfalx.bootstrap.logger.Appender warnAppender = net.microfalx.bootstrap.logger.Appender.builder("process.warn").build();
        registerAppender(warnAppender, Level.WARN);
        // log all errors in one file
        net.microfalx.bootstrap.logger.Appender errorAppender = net.microfalx.bootstrap.logger.Appender.builder("process.error").build();
        registerAppender(errorAppender, Level.ERROR);
    }

    private void registerAllAppender() {
        LoggerContext loggerContext = getLoggerContext();
        net.microfalx.bootstrap.logger.Appender allAppender = net.microfalx.bootstrap.logger.Appender.builder("process").build();
        FileAppender<ILoggingEvent> allFileAppender = createAppender(allAppender, loggerContext);
        getRootLogger().addAppender(allFileAppender);
    }

    private void registerAppender(net.microfalx.bootstrap.logger.Appender appender, Level level) {
        LoggerContext loggerContext = getLoggerContext();
        ch.qos.logback.classic.Logger logger = getRootLogger();
        FileAppender<ILoggingEvent> warnFileAppender = createAppender(appender, loggerContext);
        LevelFilter filter = new LevelFilter();
        filter.setLevel(level);
        filter.setOnMatch(FilterReply.ACCEPT);
        filter.setOnMismatch(FilterReply.DENY);
        filter.start();
        warnFileAppender.addFilter(filter);
        logger.addAppender(warnFileAppender);
    }

    private FileAppender<ILoggingEvent> createAppender(net.microfalx.bootstrap.logger.Appender appender, LoggerContext context) {
        PatternLayoutEncoder layoutEncoder = createLayoutEncoder(context);

        FixedWindowRollingPolicy rollingPolicy = createRollingPolicy(appender, context);
        SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = createTriggeringPolicy(context);

        RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
        fileAppender.setContext(context);
        fileAppender.setImmediateFlush(true);
        fileAppender.setBufferSize(FileSize.valueOf("8KB"));
        fileAppender.setFile(new File(getLogsDirectory(), appender.getFileName()).getAbsolutePath());
        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.setTriggeringPolicy(triggeringPolicy);
        fileAppender.setName(appender.getName());
        fileAppender.setEncoder(layoutEncoder);

        rollingPolicy.setParent(fileAppender);
        rollingPolicy.start();

        fileAppender.start();

        return fileAppender;
    }

    private FixedWindowRollingPolicy createRollingPolicy(net.microfalx.bootstrap.logger.Appender appender, LoggerContext context) {
        FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
        rollingPolicy.setContext(context);
        rollingPolicy.setMinIndex(1);
        rollingPolicy.setMaxIndex(getFileCount());
        rollingPolicy.setFileNamePattern(".%i.log.gz");
        return rollingPolicy;
    }

    private SizeBasedTriggeringPolicy<ILoggingEvent> createTriggeringPolicy(LoggerContext context) {
        SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = new SizeBasedTriggeringPolicy<>();
        triggeringPolicy.setContext(context);
        triggeringPolicy.setMaxFileSize(FileSize.valueOf(formatBytes(getFileSize())));
        triggeringPolicy.setCheckIncrement(Duration.buildByMinutes(60));
        triggeringPolicy.start();
        return triggeringPolicy;
    }

    private PatternLayoutEncoder createLayoutEncoder(LoggerContext context) {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSSXXX} %-5level [%-15thread] %logger{-36} :%X{}: %msg%n");
        encoder.start();
        return encoder;
    }

    private int getFileCount() {
        return environment.getProperty("bootstrap.logger.file-count", Integer.class, 5);
    }

    private long getFileSize() {
        return environment.getProperty("bootstrap.logger.file-size", Long.class, 20_000_000L);
    }

    private File getConfiguredLogsDirectory() {
        String path = environment.getProperty("bootstrap.logger.directory");
        if (isEmpty(path)) {
            File logs = new File(JvmUtils.getWorkingDirectory(false), "logs");
            if (logs.exists() && isDirectoryWritable(logs)) path = logs.getAbsolutePath();
            if (isEmpty(path)) {
                path = environment.getProperty("bootstrap.resource.directory");
                if (isNotEmpty(path)) {
                    logs = new File(path);
                    if (isDirectoryWritable(logs)) {
                        try {
                            path = FileUtils.validateDirectoryExists(new File(logs, "logs")).getAbsolutePath();
                        } catch (Exception e) {
                            // if we fail, no logs
                        }
                    }
                }
            }
        }
        return isNotEmpty(path) && new File(path).exists() && isDirectoryWritable(new File(path)) ? new File(path) : null;
    }

    private void archiveLogs() {
        String archiveFileName = "logs_archive_" + FORMATTER.format(LocalDateTime.now()) + ".zip";
        File archiveFile = new File(getLogsDirectory(), archiveFileName);
        int fileCount = 0;
        try {
            try (ZipOutputStream zipFile = new ZipOutputStream(getBufferedOutputStream(archiveFile))) {
                File[] files = getLogsDirectory().listFiles(this::acceptLogs);
                if (files != null) {
                    for (File file : files) {
                        fileCount++;
                        add(zipFile, file);
                    }
                }
            }
            if (fileCount == 0) FileUtils.remove(archiveFile);
        } catch (IOException e) {
            LOGGER.warn("Failed to archive logs to file: {}, root cause: {}", archiveFile.getAbsolutePath(), getRootCauseDescription(e));
        }

    }

    private void add(ZipOutputStream outputStream, File file) throws IOException {
        String name = file.getName();
        ZipEntry entry = new ZipEntry(name);
        entry.setSize(file.length());
        outputStream.putNextEntry(entry);
        appendStream(outputStream, getBufferedInputStream(file), false);
        outputStream.closeEntry();
        if (!("boot.log".equals(name) || RETAIN_LOG_PATTERN.matcher(name).matches())) {
            FileUtils.remove(file);
        }
    }

    private boolean acceptLogs(File file) {
        return file.isFile() && !file.getName().endsWith(".zip");
    }

    private boolean acceptArchives(File file) {
        return file.isFile() && file.getName().endsWith(".zip");
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final Pattern RETAIN_LOG_PATTERN = Pattern.compile("boot\\.(.*)\\.log");

}
