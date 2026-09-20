package net.microfalx.bootstrap.support;

import jakarta.annotation.security.RolesAllowed;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.report.Report;
import net.microfalx.bootstrap.support.report.ReportService;
import net.microfalx.bootstrap.web.application.ApplicationService;
import net.microfalx.bootstrap.web.application.Theme;
import net.microfalx.bootstrap.web.application.annotation.SystemTheme;
import net.microfalx.bootstrap.web.controller.PageController;
import net.microfalx.lang.ExceptionUtils;
import net.microfalx.resource.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import static java.nio.charset.StandardCharsets.UTF_8;

@Controller
@RequestMapping(value = "/support/report")
@SystemTheme
@RolesAllowed("admin")
@Slf4j
public class ReportController extends PageController {

    @Autowired private ReportService reportService;
    @Autowired private ApplicationService applicationService;

    @GetMapping(value = "", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<StreamingResponseBody> home(@RequestParam(value = "dynamic", defaultValue = "true") boolean dynamic,
                                                      @RequestParam(value = "secure", defaultValue = "false") boolean secure) {
        Report report = createReport().setDynamic(dynamic).setSecure(secure);
        Resource reportBody = Resource.temporary("report", "html");
        try {
            report.render(reportBody);
            return streamReport(report, reportBody);
        } catch (Exception e) {
            return streamError("Error generating report", e);
        }
    }

    @GetMapping(value = "{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<StreamingResponseBody> fragment(@PathVariable("id") String id,
                                                          @RequestParam(value = "dynamic", defaultValue = "true") boolean dynamic,
                                                          @RequestParam(value = "secure", defaultValue = "false") boolean secure) {
        Report report = createReport();
        report.setFragment(id).setDynamic(dynamic).setSecure(secure);
        Resource reportBody = Resource.temporary("report_export", ".html");
        try {
            report.render(reportBody);
            return streamReport(report, reportBody);
        } catch (Exception e) {
            return streamError("Error generating report", e);
        }
    }

    private ResponseEntity<StreamingResponseBody> streamReport(Report report, Resource reportBody) {
        LOGGER.info("Streaming report {} to client", report.getName());
        StreamingResponseBody responseBody = outputStream -> {
            try (var inputStream = reportBody.getInputStream()) {
                inputStream.transferTo(outputStream);
            }
            LOGGER.info("Report streamed successfully");
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                .body(responseBody);
    }

    private ResponseEntity<StreamingResponseBody> streamError(String message, Exception e) {
        String body = message + ": " + ExceptionUtils.getRootCauseDescription(e);
        LOGGER.error(body, e);
        StreamingResponseBody responseBody = outputStream -> outputStream.write(body.getBytes(UTF_8));
        return ResponseEntity.internalServerError()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .body(responseBody);
    }

    private Report createReport() {
        Report report = reportService.createReport();
        Theme theme = applicationService.getCurrentTheme();
        report.setTheme(theme.isLight() ? Report.Theme.LIGHT : Report.Theme.DARK);
        return report;
    }
}
