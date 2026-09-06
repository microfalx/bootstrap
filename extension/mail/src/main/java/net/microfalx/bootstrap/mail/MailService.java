package net.microfalx.bootstrap.mail;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.Issue;
import net.microfalx.bootstrap.core.async.ThreadPoolFactory;
import net.microfalx.lang.StringUtils;
import net.microfalx.lang.TimeUtils;
import net.microfalx.metrics.Metrics;
import net.microfalx.metrics.Timer;
import net.microfalx.resource.Resource;
import net.microfalx.threadpool.ThreadPool;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.unmodifiableCollection;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;
import static net.microfalx.lang.SecretUtils.maskSecret;
import static net.microfalx.lang.StringUtils.*;
import static net.microfalx.lang.TimeUtils.FIVE_MINUTE;
import static net.microfalx.lang.TimeUtils.THIRTY_SECONDS;
import static net.microfalx.resource.MimeType.TEXT_HTML;

@Service
@Slf4j
public class MailService implements InitializingBean {

    @Autowired
    private MailConfiguration configuration;

    private volatile JavaMailSenderImpl mailSender;

    private ThreadPool threadPool;
    private final PriorityQueue<FailedMessage> retryQueue = new PriorityQueue<>();
    private final Map<String, Mail> mails = new ConcurrentHashMap<>();

    /**
     * Returns a collection of mails sent or being sent by this service.
     *
     * @return a non-null instance
     */
    public Collection<Mail> getMails() {
        return unmodifiableCollection(mails.values());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initThreadPool();
        initMailSender();
        initListeners();
    }

    /**
     * Sends a MIME message contained in the resource.
     *
     * @param resource the resource containing the MIME message
     * @throws IOException   if an I/O error occurs while reading the resource
     * @throws MailException if an error occurs while sending the MIME message
     */
    public Future<Mail> send(Resource resource) throws IOException, MailException {
        requireNonNull(resource);
        MimeMessage mimeMessage = mailSender.createMimeMessage(resource.getInputStream());
        return enqueue(mimeMessage);
    }

    /**
     * Sends a MIME message.
     *
     * @param mimeMessage the resource containing the MIME message
     * @throws IOException   if an I/O error occurs while reading the resource
     * @throws MailException if an error occurs while sending the MIME message
     */
    public Future<Mail> send(MimeMessage mimeMessage) throws IOException, MailException {
        return enqueue(mimeMessage);
    }

    /**
     * Returns the JavaMail session used by this gateway.
     *
     * @return a non-null instance
     */
    public Session getSession() {
        return mailSender.getSession();
    }

    /**
     * Sends an email.
     *
     * @param to      a non-empty email address
     * @param subject a non-null subject
     * @param body    a non-null body
     */
    public Future<Mail> send(String to, String subject, Resource body) {
        return send(to, subject, body, Collections.emptyList());
    }

    /**
     * Sends an email with attachments.
     *
     * @param to          a non-empty email address
     * @param subject     a non-null subject
     * @param body        a non-null body
     * @param attachments a collection of attachments
     */
    public Future<Mail> send(String to, String subject, Resource body, Collection<Resource> attachments) {
        requireNotEmpty(to);
        requireNonNull(subject);
        requireNonNull(body);
        requireNonNull(attachments);
        MimeMessage mimeMessage = null;
        Future<Mail> future;
        Throwable throwable = null;
        try {
            mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, UTF_8.name());
            helper.setFrom(defaultIfEmpty(configuration.getFrom(), MailConfiguration.DEFAULT_FROM));
            boolean html = TEXT_HTML.equals(body.getMimeType());
            helper.setText(body.loadAsString(), html);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setSentDate(new Date());
            for (Resource resource : attachments) {
                helper.addAttachment(resource.getFileName(), new UrlResource(resource.toURL()));
            }
        } catch (Exception e) {
            throwable = e;
            registerFailure(mimeMessage, e);
        }
        if (mimeMessage != null) {
            future = enqueue(mimeMessage);
        } else {
            future = CompletableFuture.failedFuture(throwable);
        }
        return future;
    }

    private void reload() {
        initMailSender();
    }

    private Future<Mail> enqueue(MimeMessage mimeMessage) {
        requireNonNull(mimeMessage);
        Mail mail = new Mail(mimeMessage);
        mails.put(mail.getId(), mail);
        return threadPool.submit(() -> {
            doSend(mail);
            return mail;
        });
    }

    private void doSend(Mail mail) {
        requireNonNull(mail);
        MimeMessage mimeMessage = mail.getMimeMessage();
        String address = getFirstAddress(mimeMessage);
        try (Timer ignored = MAIL_SENDING.startTimer(address)) {
            mailSender.send(mimeMessage);
            mail.sent();
            Arrays.stream(mimeMessage.getAllRecipients()).forEach(a -> MAIL_SENT.count(getAddress(a)));
        } catch (Exception e) {
            mail.failed(e);
            registerFailure(mimeMessage, e);
        }
    }

    private void registerFailure(MimeMessage mimeMessage, Exception e) {
        String address = StringUtils.NA_STRING;
        if (mimeMessage != null) {
            address = getFirstAddress(mimeMessage);
        }
        MAIL_FAILED.count(address);
        Issue.create(Issue.Type.STABILITY, "Mail")
                .withDescription(e, "Failed to send email to ''{0}''", address)
                .withSeverity(Issue.Severity.HIGH).withAttributeCounter(address)
                .register();
    }

    private void initThreadPool() {
        threadPool = ThreadPoolFactory.create("Mail").setRatio(0.5f).create();
        threadPool.scheduleAtFixedRate(new MaintenanceTask(), configuration.getRetryInterval());
    }

    private void initListeners() {
        configuration.addListener(event -> {
            LOGGER.info("Settings changed for group '{}', reload", event.getKey());
            reload();
        });
    }

    private void initMailSender() {
        mailSender = new JavaMailSenderImpl();
        mailSender.setHost(configuration.getHost());
        mailSender.setPort(configuration.getPort());
        Properties props = mailSender.getJavaMailProperties();
        if (isNotEmpty(configuration.getUserName())) {
            props.put("mail.smtp.auth", "true");
            mailSender.setUsername(configuration.getUserName());
            mailSender.setPassword(configuration.getPassword());
        } else {
            props.put("mail.smtp.auth", "false");
        }
        props.put("mail.smtp.ssl.enable", configuration.isTls());
        props.put("mail.smtp.starttls.enable", !configuration.isTls());
        LOGGER.info("SMTP settings: {}, port: {}, user: {}", configuration.getHost(), configuration.getPort(),
                maskSecret(configuration.getUserName()));
    }

    private static String getFirstAddress(MimeMessage mimeMessage) {
        try {
            Address[] allRecipients = mimeMessage.getAllRecipients();
            if (allRecipients != null && allRecipients.length > 0) {
                return getAddress(allRecipients[0]);
            } else {
                return NA_STRING;
            }
        } catch (MessagingException e) {
            return NA_STRING;
        }
    }

    private static String getAddress(Address address) {
        if (address instanceof InternetAddress internetAddress) {
            return internetAddress.getAddress();
        } else if (address != null) {
            return address.toString();
        } else {
            return NA_STRING;
        }
    }

    private static final Metrics MAIL = Metrics.of("Mail");
    private static final Metrics MAIL_SENDING = MAIL.withGroup("Sending");
    private static final Metrics MAIL_SENT = MAIL.withGroup("Sent");
    private static final Metrics MAIL_FAILED = MAIL.withGroup("Failed");

    private class MaintenanceTask implements Runnable {

        private void send() {
            int rescheduled = 0;
            while (!retryQueue.isEmpty()) {
                FailedMessage failedMessage = retryQueue.poll();
                if (!failedMessage.isExpired()) {
                    rescheduled++;
                    doSend(failedMessage.mail);
                } else {
                    break;
                }
            }
            if (rescheduled > 0) {
                LOGGER.info("Rescheduled {} failed messages for retry", rescheduled);
            }
        }

        private void cleanup() {
            LocalDateTime threshold = LocalDateTime.now().minus(configuration.getRetention());
            Collection<String> toBeRemoved = mails.values().stream()
                    .filter(mail -> mail.getCreatedAt().isBefore(threshold))
                    .map(Mail::getId).toList();
            toBeRemoved.forEach(mails::remove);
            if (!toBeRemoved.isEmpty()) {
                LOGGER.info("Removed {} mails from history", toBeRemoved.size());
            }
        }

        @Override
        public void run() {
            send();
            cleanup();
        }
    }

    private static class FailedMessage implements Delayed {

        private final Mail mail;
        private final long delay;

        FailedMessage(Mail mail) {
            this.mail = mail;
            this.delay = mail.getRetryCount() == 0 ? 0 : getDelay(mail.getRetryCount());
        }

        @Override
        public long getDelay(@NotNull TimeUnit unit) {
            return TimeUnit.MILLISECONDS.convert(delay, unit);
        }

        @Override
        public int compareTo(@NotNull Delayed o) {
            if (!(o instanceof FailedMessage)) return -1;
            return this.mail.getCreatedAt().compareTo(((FailedMessage) o).mail.getCreatedAt());
        }

        private boolean isExpired() {
            return TimeUtils.millisSince(this.mail.getCreatedAt()) >= TimeUtils.ONE_HOUR;
        }

        private long getDelay(int retryCount) {
            return Math.max(MAX_INTERVAL, (long) (INITIAL_INTERVAL * Math.pow(MULTIPLIER, retryCount - 1)));
        }
    }

    private static final long INITIAL_INTERVAL = THIRTY_SECONDS;
    private static final double MULTIPLIER = 2.0;
    private static final long MAX_INTERVAL = FIVE_MINUTE;

}
