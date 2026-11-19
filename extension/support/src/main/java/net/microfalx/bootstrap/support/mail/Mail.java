package net.microfalx.bootstrap.support.mail;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.microfalx.bootstrap.dataset.annotation.Formattable;
import net.microfalx.bootstrap.dataset.annotation.OrderBy;
import net.microfalx.bootstrap.dataset.model.Formatters;
import net.microfalx.bootstrap.dataset.model.NamedIdentityAware;
import net.microfalx.lang.ExceptionUtils;
import net.microfalx.lang.annotation.*;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

/**
 * A model which exposes a {@link net.microfalx.bootstrap.mail.Mail} in a data set.
 */
@Getter
@Setter
@ToString
@Name("Mails")
@ReadOnly
@Visible(value = false, fieldNames = "description")
public class Mail extends NamedIdentityAware<String> {

    @Position(10)
    @Description("The recipient of the mail")
    @Width("200px")
    private String to;

    @Position(11)
    @Description("The sender of the mail")
    @Width("200px")
    private String from;

    @Position(15)
    @Description("The status of the mail")
    @Width("80px")
    private net.microfalx.bootstrap.mail.Mail.Status status;

    @Position(20)
    @Label(value = "Attempts", group = "Counts")
    @Description("The number of attempts to send the mail")
    @Width("70px")
    private int attemptCount;

    @Position(21)
    @Label(value = "Retries", group = "Counts")
    @Description("The number of retries after a failure")
    @Width("70px")
    private int retryCount;

    @Position(30)
    @Label(value = "Attempted", group = "Timestamps")
    @Description("The last time when an attempt to send the mail was made")
    @Width("80px")
    @Formattable(elapsed = true)
    private LocalDateTime attemptedAt;

    @Position(31)
    @Label(value = "Sent", group = "Timestamps")
    @Description("The time when the mail was sent")
    @Width("80px")
    @Formattable(elapsed = true)
    private LocalDateTime sentAt;

    @Position(32)
    @Label(value = "Failed", group = "Timestamps")
    @Description("The last time when the mail failed to be sent")
    @Width("80px")
    @Formattable(elapsed = true)
    private LocalDateTime failedAt;

    @Position(50)
    @Description("The root cause of the last failure")
    @Width("30%")
    private String failure;

    @Position(100)
    @Description("The time when the mail was submitted")
    @Width("80px")
    @OrderBy(OrderBy.Direction.DESC)
    @CreatedDate
    @CreatedAt
    @Formattable(tooltip = Formatters.CreatedAtTooltip.class, elapsed = true)
    private LocalDateTime createdAt;

    public static Mail from(net.microfalx.bootstrap.mail.Mail mail) {
        if (mail == null) return null;
        Mail model = new Mail();
        model.setId(mail.getId());
        model.setName(mail.getSubject());
        model.setTo(mail.getTo());
        model.setFrom(mail.getFrom());
        model.setStatus(mail.getStatus());
        model.setAttemptCount(mail.getAttemptCount().get());
        model.setRetryCount(mail.getRetryCount());
        model.setAttemptedAt(mail.getAttemptedAt());
        model.setSentAt(mail.getSentAt());
        model.setFailedAt(mail.getFailedAt());
        model.setCreatedAt(mail.getCreatedAt());
        if (mail.getThrowable() != null) {
            model.setFailure(ExceptionUtils.getRootCauseDescription(mail.getThrowable()));
        }
        return model;
    }
}
