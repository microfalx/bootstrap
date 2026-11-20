package net.microfalx.bootstrap.support.report;

import net.microfalx.bootstrap.dataset.DataSetException;
import net.microfalx.bootstrap.dataset.DataSetService;
import net.microfalx.bootstrap.dataset.annotation.DataSet;
import net.microfalx.bootstrap.help.annotation.Help;
import net.microfalx.bootstrap.model.Field;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.bootstrap.web.component.Button;
import net.microfalx.bootstrap.web.component.Item;
import net.microfalx.bootstrap.web.component.Menu;
import net.microfalx.bootstrap.web.component.Toolbar;
import net.microfalx.bootstrap.web.dataset.SystemDataSetController;
import net.microfalx.bootstrap.web.util.JsonResponse;
import net.microfalx.lang.EnumUtils;
import net.microfalx.resource.Resource;
import net.microfalx.threadpool.ThreadPool;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.microfalx.lang.StringUtils.isEmpty;

/**
 * A dashboard with the rendered reports sent out by the {@link ReportService}.
 * <p>
 * The bean has an explicit name because {@link net.microfalx.bootstrap.support.ReportController} (the controller
 * which renders the current report) has the same simple name.
 */
@Slf4j
@Controller(ReportController.BEAN_NAME)
@RequestMapping(value = "/support/reports")
@DataSet(model = Report.class, viewTemplate = "support/view_report", viewClasses = "modal-xl", canDownload = true)
@Help("support/report")
public class ReportController extends SystemDataSetController<Report, String> {

    static final String BEAN_NAME = "reportDashboardController";

    /**
     * Reports are rendered in a sandbox (scripts are allowed for the report navigation, but the report runs in a
     * unique origin), even when the content is opened outside the iframe.
     */
    private static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";
    private static final String CONTENT_SECURITY_POLICY = "sandbox allow-scripts";

    private static final String GENERATE_ACTION = "report.generate";
    private static final String SEND_ACTION = "report.send";
    private static final String INTERVAL_PARAMETER = "interval";

    private final ReportService reportService;
    private final ThreadPool threadPool;

    public ReportController(DataSetService dataSetService, ReportService reportService, ThreadPool threadPool) {
        super(dataSetService);
        this.reportService = reportService;
        this.threadPool = threadPool;
    }

    /**
     * Sends (in the background) a report covering the requested interval.
     *
     * @param intervalName the name of the interval ({@link Interval})
     * @return the response
     */
    @PostMapping("send")
    @ResponseBody()
    public final JsonResponse<?> send(@RequestParam(INTERVAL_PARAMETER) String intervalName) {
        Interval interval = EnumUtils.fromName(Interval.class, intervalName);
        threadPool.execute(() -> {
            try {
                reportService.send(interval.duration);
            } catch (Exception e) {
                LOGGER.atError().setCause(e).log("Failed to send report for the {}", interval.label);
            }
        });
        return JsonResponse.success("Sending report for the " + interval.label
                + ", it will be available shortly");
    }

    /**
     * Returns the summary report (the body of the message) inline, to be displayed inside an iframe.
     *
     * @param id the identifier of the report
     * @return the summary report
     */
    @GetMapping("{id}/summary")
    @ResponseBody()
    public final ResponseEntity<String> summary(@PathVariable("id") String id) {
        return render(id, net.microfalx.argus.report.Report.Content::getSummary);
    }

    /**
     * Returns the full report inline, to be displayed inside an iframe.
     *
     * @param id the identifier of the report
     * @return the full report
     */
    @GetMapping("{id}/content")
    @ResponseBody()
    public final ResponseEntity<String> content(@PathVariable("id") String id) {
        return render(id, net.microfalx.argus.report.Report.Content::getContent);
    }

    @Override
    protected void beforeView(net.microfalx.bootstrap.dataset.DataSet<Report, Field<Report>, String> dataSet,
                              Model controllerModel, Report dataSetModel) {
        super.beforeView(dataSet, controllerModel, dataSetModel);
        if (dataSetModel == null) return;
        reportService.getContent(dataSetModel.getId()).ifPresent(content -> {
            controllerModel.addAttribute("hasSummary", !isEmpty(content.getSummary()));
            controllerModel.addAttribute("hasContent", !isEmpty(content.getContent()));
        });
    }

    @Override
    protected Resource download(net.microfalx.bootstrap.dataset.DataSet<Report, Field<Report>, String> dataSet,
                                Model controllerModel, Report dataSetModel) {
        return reportService.getArchive(dataSetModel.getId())
                .orElseThrow(() -> new DataSetException("An archive is not available for report '"
                        + dataSetModel.getName() + "'"));
    }

    @Override
    protected void updateToolbar(Toolbar toolbar) {
        super.updateToolbar(toolbar);
        toolbar.add(new Button().setAction(GENERATE_ACTION).setText("Generate").setIcon("fa-solid fa-file-waveform")
                .setDescription("Generates a report with the current state of the application (opens in a new tab)"));
        toolbar.add(new Button().setText("Send").setIcon("fa-solid fa-paper-plane")
                .setDescription("Sends a report to the configured recipients").setMenu(createSendMenu()));
    }

    private Menu createSendMenu() {
        Menu menu = new Menu().setId("send");
        for (Interval interval : Interval.values()) {
            menu.add(new Item().setAction(SEND_ACTION).setText(interval.text).setIcon("fa-solid fa-clock")
                    .setDescription("Sends a report covering the " + interval.label)
                    .addParameter(INTERVAL_PARAMETER, interval.name()));
        }
        return menu;
    }

    private ResponseEntity<String> render(String id,
                                          Function<net.microfalx.argus.report.Report.Content, String> extractor) {
        net.microfalx.argus.report.Report.Content content = reportService.getContent(id)
                .orElseThrow(() -> new DataSetException("Report with identifier '" + id + "' does not exist"));
        String html = extractor.apply(content);
        if (isEmpty(html)) throw new DataSetException("Report '" + content.getName() + "' has no content");
        return ResponseEntity.ok().contentType(new MediaType(MediaType.TEXT_HTML, UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header(CONTENT_SECURITY_POLICY_HEADER, CONTENT_SECURITY_POLICY)
                .body(html);
    }

    /**
     * The intervals available when a report is sent on request.
     */
    private enum Interval {

        HOUR("Last Hour", "last hour", Duration.ofHours(1)),
        DAY("Last Day", "last day", Duration.ofDays(1)),
        WEEK("Last Week", "last week", Duration.ofDays(7));

        private final String text;
        private final String label;
        private final Duration duration;

        Interval(String text, String label, Duration duration) {
            this.text = text;
            this.label = label;
            this.duration = duration;
        }
    }
}
