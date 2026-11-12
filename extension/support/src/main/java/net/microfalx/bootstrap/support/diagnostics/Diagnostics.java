package net.microfalx.bootstrap.support.diagnostics;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.microfalx.argus.api.Failure;
import net.microfalx.argus.diagnostics.Diagnostics.Type;
import net.microfalx.argus.diagnostics.*;
import net.microfalx.bootstrap.dataset.annotation.Formattable;
import net.microfalx.bootstrap.dataset.annotation.OrderBy;
import net.microfalx.bootstrap.dataset.model.Formatters;
import net.microfalx.bootstrap.dataset.model.IdentityAware;
import net.microfalx.lang.ExceptionUtils;
import net.microfalx.lang.annotation.*;
import org.springframework.data.annotation.CreatedDate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Getter
@Setter
@ToString
@Name("Diagnostics")
@ReadOnly
public class Diagnostics extends IdentityAware<String> {

    @Position(3)
    @Name
    @Description("The name of the diagnostics")
    @Width("20%")
    private String name;

    @Position(5)
    @Description("The type of the diagnostics")
    @Width("140px")
    private Type type;

    @Position(7)
    @Description("The state of the diagnostics")
    @Width("90px")
    private State state;

    @Position(20)
    @Description("The size of the collected data")
    @Width("80px")
    @Formattable(unit = Formattable.Unit.BYTES, negativeValue = Formattable.NA)
    private long size;

    @Position(100)
    @Label("Started At")
    @Description("The time when the diagnostics collection started")
    @Width("100px")
    @CreatedDate
    @CreatedAt
    @OrderBy(OrderBy.Direction.DESC)
    @Timestamp
    @Formattable(tooltip = Formatters.CreatedAtTooltip.class, elapsed = true)
    private LocalDateTime startedAt;

    @Position(101)
    @Description("The time it took to collect the diagnostics")
    @Width("80px")
    @Formattable(unit = Formattable.Unit.MILLI_SECOND, zeroValue = Formattable.NA)
    private long duration;

    @Position(150)
    @Description("The reason why the diagnostics was collected")
    @Width("30%")
    private String description;

    @Position(200)
    @Visible(value = false)
    private String failureMessage;

    @Position(201)
    @Visible(value = false)
    private String failureStackTrace;

    public static Diagnostics from(net.microfalx.argus.diagnostics.Diagnostics diagnostics) {
        if (diagnostics == null) return null;
        Diagnostics model = new Diagnostics();
        model.setId(diagnostics.getId());
        model.setName(diagnostics.getName());
        model.setDescription(diagnostics.getDescription());
        model.setType(getType(diagnostics));
        model.setState(getState(diagnostics));
        model.setSize(diagnostics.getSize());
        Duration duration = diagnostics.getDuration();
        model.setDuration(duration != null ? duration.toMillis() : 0);
        model.setStartedAt(toLocalDateTime(diagnostics.getStartTime()));
        updateFailure(model, diagnostics.getFailure());
        return model;
    }

    private static void updateFailure(Diagnostics model, Failure failure) {
        if (failure == null) return;
        model.setFailureMessage(failure.getRootCauseDescription());
        if (failure.getThrowable() != null) {
            model.setFailureStackTrace(ExceptionUtils.getStackTrace(failure.getThrowable()));
        }
    }

    private static State getState(net.microfalx.argus.diagnostics.Diagnostics diagnostics) {
        if (diagnostics.getFailure() != null) return State.FAILED;
        if (diagnostics.getStartTime() == null) return State.PENDING;
        if (diagnostics.getEndTime() == null) return State.RUNNING;
        return State.COMPLETED;
    }

    /**
     * Returns the type of diagnostics based on its class, since {@code Diagnostics.getType()} is not public.
     */
    private static Type getType(net.microfalx.argus.diagnostics.Diagnostics diagnostics) {
        if (diagnostics instanceof HeapDump) return Type.HEAP_DUMP;
        if (diagnostics instanceof Histogram) return Type.HISTOGRAM;
        if (diagnostics instanceof ThreadDump) return Type.THREAD_DUMP;
        if (diagnostics instanceof VmReport) return Type.VM_REPORT;
        if (diagnostics instanceof FlightRecording) return Type.FLIGHT_RECORDING;
        if (diagnostics instanceof Profile) return Type.PROFILE;
        return null;
    }

    private static LocalDateTime toLocalDateTime(ZonedDateTime dateTime) {
        return dateTime != null ? dateTime.toLocalDateTime() : null;
    }

    public enum State {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }
}
