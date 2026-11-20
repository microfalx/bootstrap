package net.microfalx.bootstrap.support.report;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.microfalx.bootstrap.dataset.annotation.Formattable;
import net.microfalx.bootstrap.dataset.annotation.OrderBy;
import net.microfalx.bootstrap.dataset.model.Formatters;
import net.microfalx.bootstrap.dataset.model.NamedIdentityAware;
import net.microfalx.lang.annotation.*;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A model which exposes a rendered report which was sent out ({@link net.microfalx.argus.report.Report.Content})
 * in a data set.
 */
@Getter
@Setter
@ToString
@Name("Reports")
@ReadOnly
@Visible(value = false, fieldNames = "description")
public class Report extends NamedIdentityAware<String> {

    @Position(10)
    @Label(value = "Summary", group = "Size")
    @Description("The size of the summary report (the body of the message)")
    @Width("80px")
    @Formattable(unit = Formattable.Unit.BYTES)
    private long summarySize;

    @Position(11)
    @Label(value = "Report", group = "Size")
    @Description("The size of the full report")
    @Width("80px")
    @Formattable(unit = Formattable.Unit.BYTES)
    private long contentSize;

    @Position(20)
    @Description("Indicates whether the archive (encrypted full report) is available for download")
    @Width("70px")
    private boolean archive;

    @Position(100)
    @Label("Sent At")
    @Description("The time when the report was sent")
    @Width("80px")
    @OrderBy(OrderBy.Direction.DESC)
    @CreatedDate
    @CreatedAt
    @Formattable(tooltip = Formatters.CreatedAtTooltip.class, elapsed = true)
    private LocalDateTime createdAt;

    /**
     * Creates a data set model from a rendered report.
     *
     * @param content the rendered report
     * @return the model, null if the content is null
     */
    public static Report from(net.microfalx.argus.report.Report.Content content) {
        if (content == null) return null;
        Report model = new Report();
        model.setId(content.getId());
        model.setName(content.getName());
        model.setSummarySize(getSize(content.getSummary()));
        model.setContentSize(getSize(content.getContent()));
        model.setArchive(content.hasArchive());
        model.setCreatedAt(content.getCreatedAt());
        return model;
    }

    private static long getSize(String text) {
        return text != null ? text.getBytes(UTF_8).length : 0;
    }
}
