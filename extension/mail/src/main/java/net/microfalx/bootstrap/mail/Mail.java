package net.microfalx.bootstrap.mail;

import jakarta.activation.DataHandler;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.ParseException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.lang.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;

/**
 * Holds information about the status of a mail sending operation.
 */
@Slf4j
@Getter
@ToString
public class Mail implements Identifiable<String>, Nameable {

    private String id;
    private String subject = StringUtils.NA_STRING;
    private String to = StringUtils.NA_STRING;
    private String from = StringUtils.NA_STRING;
    volatile Status status = Status.PENDING;
    private final LocalDateTime createdAt = LocalDateTime.now();
    private final AtomicInteger attemptCount = new AtomicInteger();
    @Getter(AccessLevel.NONE) final AtomicInteger retryCount = new AtomicInteger();
    volatile LocalDateTime attemptedAt;
    volatile LocalDateTime sentAt;
    volatile LocalDateTime failedAt;
    volatile Throwable throwable;
    @ToString.Exclude private String body;
    private boolean html;

    @Getter(AccessLevel.PACKAGE)
    private volatile MimeMessage mimeMessage;

    Mail(MimeMessage mimeMessage) {
        requireNotEmpty(mimeMessage);
        this.mimeMessage = mimeMessage;
        try {
            this.id = mimeMessage.getMessageID();
            this.to = mimeMessage.getRecipients(MimeMessage.RecipientType.TO)[0].toString();
            this.from = mimeMessage.getFrom()[0].toString();
            this.subject = mimeMessage.getSubject();
        } catch (MessagingException e) {
            // ignore, use default values
        }
        if (StringUtils.isEmpty(this.id)) {
            this.id = IdGenerator.get().nextAsString();
        }
        extractBody(mimeMessage);
    }

    /**
     * Returns whether the mail has a body (HTML or plain text) which can be displayed.
     *
     * @return {@code true} if a body is available, {@code false} otherwise
     */
    public boolean hasBody() {
        return body != null;
    }

    /**
     * Extracts the body of the message, preferring the HTML version over the plain text version.
     * <p>
     * The body is extracted when the mail is created because the MIME message is released once the mail is sent.
     *
     * @param mimeMessage the MIME message
     */
    private void extractBody(MimeMessage mimeMessage) {
        try {
            Part part = findBodyPart(mimeMessage, TEXT_HTML_TYPE);
            if (part == null) part = findBodyPart(mimeMessage, TEXT_PLAIN_TYPE);
            if (part == null) return;
            this.body = (String) part.getContent();
            this.html = isMimeType(part, TEXT_HTML_TYPE);
        } catch (MessagingException | IOException e) {
            LOGGER.debug("Failed to extract body for mail with subject ''{}'', root cause: {}",
                    TextUtils.abbreviate(subject, 50), ExceptionUtils.getRootCauseDescription(e));
        }
    }

    private static Part findBodyPart(Part part, String mimeType) throws MessagingException, IOException {
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                Part found = findBodyPart(multipart.getBodyPart(i), mimeType);
                if (found != null) return found;
            }
            return null;
        }
        boolean attachment = Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition());
        boolean candidate = content instanceof String && !attachment && isMimeType(part, mimeType);
        return candidate ? part : null;
    }

    /**
     * Checks the content type using the data handler, which is reliable for both parsed messages and messages
     * created in memory (the Content-Type header is only updated when the message is saved).
     */
    private static boolean isMimeType(Part part, String mimeType) throws MessagingException {
        DataHandler dataHandler = part.getDataHandler();
        if (dataHandler == null || dataHandler.getContentType() == null) return false;
        try {
            return new ContentType(dataHandler.getContentType()).match(mimeType);
        } catch (ParseException e) {
            return false;
        }
    }

    @Override
    public String getName() {
        return subject;
    }

    public int getRetryCount() {
        return retryCount.get();
    }

    void attempt() {
        attemptedAt = LocalDateTime.now();
        attemptCount.incrementAndGet();
        LOGGER.debug(Glyph.ENVELOPE_ARROW + " attempt to send mail to ''{}'' with subject ''{}''",
                to, TextUtils.abbreviate(subject, 50));
    }

    void sent() {
        this.status = Status.SENT;
        this.sentAt = LocalDateTime.now();
        this.throwable = null;
        this.mimeMessage = null;
        LOGGER.info("Mail sent to ''{}'' with subject ''{}''",
                to, TextUtils.abbreviate(subject, 50));
    }

    void failed(Throwable throwable) {
        this.status = Status.FAILED;
        this.failedAt = LocalDateTime.now();
        this.throwable = throwable;
        if (retryCount.get() == 0) {
            LOGGER.info("Mail failed to send to ''{}'' with subject ''{}'', root cause: {}",
                    to, TextUtils.abbreviate(subject, 50), ExceptionUtils.getRootCauseDescription(throwable));
        } else if ((retryCount.get() % 5) == 0) {
            LOGGER.error("Mail failed to send to ''{}'' with subject ''{}'', root cause: {}, retry count: {}",
                    to, TextUtils.abbreviate(subject, 50), ExceptionUtils.getRootCauseDescription(throwable), retryCount.get());
        }
        this.retryCount.incrementAndGet();
    }

    public enum Status {
        PENDING, SENT, FAILED
    }

    private static final String TEXT_HTML_TYPE = "text/html";
    private static final String TEXT_PLAIN_TYPE = "text/plain";
}
