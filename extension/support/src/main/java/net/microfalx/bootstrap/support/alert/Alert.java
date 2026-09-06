package net.microfalx.bootstrap.support.alert;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.microfalx.argus.api.LoggerEvent;
import net.microfalx.bootstrap.dataset.annotation.Formattable;
import net.microfalx.bootstrap.dataset.annotation.OrderBy;
import net.microfalx.bootstrap.dataset.model.Formatters;
import net.microfalx.bootstrap.dataset.model.IdentityAware;
import net.microfalx.lang.annotation.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@Name("Alerts")
@ReadOnly
public class Alert extends IdentityAware<String> {

    @Position(3)
    @Name
    @Description("The message associated with the alert")
    @Width("50%")
    private String message;

    @Position(10)
    @Label(value = "Failure Type")
    @Width("120px")
    private String failureType;

    @Position(11)
    @Description("The severity level")
    @Width("70px")
    private LoggerEvent.Level level;

    @Position(15)
    @Label(value = "Ack.")
    @Description("Indicates whether the alert has been acknowledged")
    @Width("70px")
    private boolean acknowledged;

    @Position(20)
    @Label(value = "Total", group = "Counts")
    @Description("The total number of alerts received")
    @Width("70px")
    private long totalCount;

    @Position(21)
    @Label(value = "Pending", group = "Counts")
    @Description("The total number of alerts received since the last acknowledgement")
    @Width("70px")
    private long pendingCount;

    @Position(31)
    @Label(value = "Exception Class")
    @Visible(false)
    private String failureClass;

    @Position(100)
    @Description("The time when the alert started to fire")
    @Width("120px")
    @CreatedDate
    @CreatedAt
    @Formattable(tooltip = Formatters.CreatedAtTooltip.class, elapsed = true)
    private LocalDateTime createdAt;

    @Position(101)
    @Description("The last time when the alert fired")
    @Width("120px")
    @OrderBy(OrderBy.Direction.DESC)
    @Formattable(tooltip = Formatters.ModifiedAtTooltip.class, elapsed = true)
    @LastModifiedDate
    @ModifiedAt
    @Timestamp
    private LocalDateTime modifiedAt;

    public static Alert from(net.microfalx.argus.api.Alert event) {
        if (event == null) return null;
        Alert model = new Alert();
        model.setId(event.getId());
        model.setCreatedAt(event.getCreatedAt());
        model.setModifiedAt(event.getModifiedAt());
        model.setLevel(event.getLevel());
        model.setMessage(event.getMessage());
        model.setAcknowledged(event.isAcknowledged());
        model.setFailureType(event.getFailureType());
        model.setFailureClass(event.getFailureClass());
        model.setTotalCount(event.getTotalEventCount());
        model.setPendingCount(event.getPendingEventCount());
        return model;
    }
}
