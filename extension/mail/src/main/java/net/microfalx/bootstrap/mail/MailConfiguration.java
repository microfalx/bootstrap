package net.microfalx.bootstrap.mail;

import net.microfalx.bootstrap.configuration.ConfigurationListenerAware;
import net.microfalx.bootstrap.configuration.annotation.ConfigurationMapping;
import net.microfalx.lang.annotation.DefaultValue;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The configuration for Mail service.
 */
@Component
@ConfigurationMapping(prefix = "bootstrap.mail")
public interface MailConfiguration extends ConfigurationListenerAware {

    String DEFAULT_FROM = "admin@local";

    /**
     * Returns the hostname of the SMTP gateway
     *
     * @return a non-null string
     */
    @DefaultValue("localhost")
    String getHost();

    /**
     * Returns the port of the SMTP gateway.
     *
     * @return a positive integer
     */
    @DefaultValue("25")
    int getPort();

    /**
     * Returns whether the SMTP gateway requires TLS.
     * <p>
     * This setting is mapped to the JavaMail property "mail.smtp.ssl.enable" and "mail.smtp.starttls.enable".
     * If set to true, the connection will not be upgraded to TLS, otherwise the connection will be upgraded to TLS
     * using the STARTTLS command.
     *
     * @return <code>true</code> true if TLS is required, <code>false</code>
     */
    @DefaultValue("false")
    boolean isTls();

    /**
     * Returns the username to authenticate with the SMTP gateway
     *
     * @return the username, null/empty if authentication is not required
     */
    String getUserName();

    /**
     * Returns the password to authenticate with the SMTP gateway
     *
     * @return the password, null/empty if authentication is not required
     */
    String getPassword();

    /**
     * Returns a space separated list of hostnames or IP addresses which should be trusted when using SSL/TLS.
     * <p>
     * This setting is mapped to the JavaMail property "mail.smtp.ssl.trust". If set to "*", all hosts will be trusted.
     * If set to a list of hostnames or IP addresses, only those hosts will be trusted.
     * <p>
     * Setting the value to "*" trusts certificates for all SMTP hosts, which is not recommended for production environments.
     * It is better to specify the exact hostnames or IP addresses of the SMTP servers that should be trusted.
     *
     * @return a space separated list of hostnames or IP addresses
     */
    String getTlsTrust();

    /**
     * Returns whether the server identity should be checked when using SSL/TLS.
     * <p>
     * This setting is mapped to the JavaMail property "mail.smtp.ssl.checkserveridentity".
     * If set to true, the server's identity will be checked against the certificate presented during
     * the SSL/TLS handshake. If set to false, the server's identity will not be checked, which may be
     * useful for testing or when using self-signed certificates.
     *
     * @return {@code true} if the server identity should be checked, {@code false} otherwise
     */
    @DefaultValue("true")
    boolean isTlsCheckServerIdentity();

    /**
     * Returns the email address to be used with emails send from the application.
     *
     * @return a non-null instance
     */
    @DefaultValue(MailConfiguration.DEFAULT_FROM)
    String getFrom();

    /**
     * Returns the maximum retry count in case of failures.
     *
     * @return a positive integer
     */
    @DefaultValue("10")
    int getMaximumRetryCount();

    /**
     * Returns the retention of sent emails.
     *
     * @return a non-null instance
     */
    @DefaultValue("7d")
    Duration getRetention();

    /**
     * Returns the retry interval.
     *
     * @return a positive integer
     */
    @DefaultValue("60s")
    Duration getRetryInterval();
}
