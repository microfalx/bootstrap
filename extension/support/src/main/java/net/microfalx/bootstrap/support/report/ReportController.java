package net.microfalx.bootstrap.support.report;

import net.microfalx.bootstrap.dataset.DataSetException;
import net.microfalx.bootstrap.dataset.DataSetService;
import net.microfalx.bootstrap.dataset.annotation.DataSet;
import net.microfalx.bootstrap.help.annotation.Help;
import net.microfalx.bootstrap.model.Field;
import net.microfalx.bootstrap.web.component.Button;
import net.microfalx.bootstrap.web.component.Toolbar;
import net.microfalx.bootstrap.web.dataset.SystemDataSetController;
import net.microfalx.resource.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.microfalx.lang.StringUtils.isEmpty;

/**
 * A dashboard with the rendered reports sent out by the {@link ReportService}.
 * <p>
 * The bean has an explicit name because {@link net.microfalx.bootstrap.support.ReportController} (the controller
 * which renders the current report) has the same simple name.
 */
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

    private final ReportService reportService;

    public ReportController(DataSetService dataSetService, ReportService reportService) {
        super(dataSetService);
        this.reportService = reportService;
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
}
