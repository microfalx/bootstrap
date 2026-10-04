package net.microfalx.bootstrap.support.report;

import net.microfalx.bootstrap.configuration.ConfigurationListenerAware;
import net.microfalx.bootstrap.configuration.annotation.ConfigurationMapping;
import net.microfalx.lang.annotation.DefaultValue;
import org.springframework.stereotype.Component;

/**
 * The configuration for Mail service.
 */
@Component
@ConfigurationMapping(prefix = "bootstrap.support.report")
public interface ReportConfiguration extends ConfigurationListenerAware {

    /**
     * Returns whether the system report is enabled.
     *
     * @return {@code true} if enabled, <code>false</code> otherwise
     */
    boolean isEnabled();

    /**
     * Returns whether the system report should be generated on boot.
     *
     * @return {@code true} if a report is sent out after application was started, {@code false} otherwise
     */
    boolean isOnBoot();

    /**
     * Returns whether the system report should be sent when a notice is detected.
     *
     * @return {@code true} for notices, {@code false} otherwise
     */
    boolean isOnNotice();

    /**
     * Return the daily report daily schedule in CRON format.
     * <p>
     * The daily schedule is sent out every day at midnight.
     *
     * @return the CRON expression
     */
    @DefaultValue("0 0 0 * * ?")
    String getDailySchedule();

    /**
     * Returns regular issues report schedule in CRON format.
     * <p>
     * A report when application has issues is sent at this time.
     *
     * @return the CRON expression
     */
    @DefaultValue("0 0 0/6 ? * * *")
    String getWithIssuesSchedule();

    /**
     * Returns the critical report schedule in CRON format.
     * <p>
     * A report when application has critical issues is sent at this time.
     *
     * @return the CRON expression
     */
    @DefaultValue("0 0/15 * ? * * *")
    String getWithCriticalIssuesSchedule();

    /**
     * Returns the threshold of issues less than critical to trigger the report.
     *
     * @return a positive integer
     */
    @DefaultValue("10")
    int getHighIssuesThreshold();

    /**
     * Returns the threshold of critical issues to trigger the report.
     *
     * @return a positive integer
     */
    @DefaultValue("1")
    int getCriticalIssuesThreshold();

    /**
     * Returns a comma-separated list of report recipients (email addresses).
     * <p>
     * If the list is empty, the report will be sent to all admins.
     *
     * @return the recipients
     */
    String getRecipients();

    /**
     * The password used to encrypt the report attachment.
     * <p>
     * If empty, no encryption will be applied. While a default password is provided, it is strongly recommended to
     * change it to a secure value to ensure the confidentiality of the report data (the report might contain sensitive
     * information).
     *
     * @return the password.
     */
    @DefaultValue("WzeXYN1j4S675lLsL4Vy")
    String getPassword();

    /**
     * A name of the reporting system.
     * <p>
     * If the list is empty, the hostname will be used.
     *
     * @return the application/system name
     */
    @DefaultValue("Bootstrap")
    String getSystemName();

    /**
     * Returns the token used to protect the reports.
     *
     * @return the report token
     */
    @DefaultValue("UdDLyEprdTXZU6XHJCGg")
    String getReportToken();

}
