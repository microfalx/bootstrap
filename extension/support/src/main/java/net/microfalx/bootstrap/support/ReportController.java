package net.microfalx.bootstrap.support;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.Failure;
import net.microfalx.argus.report.Report;
import net.microfalx.bootstrap.security.SecurityContext;
import net.microfalx.bootstrap.support.report.ReportService;
import net.microfalx.bootstrap.web.application.ApplicationService;
import net.microfalx.bootstrap.web.application.Theme;
import net.microfalx.bootstrap.web.application.annotation.SystemTheme;
import net.microfalx.bootstrap.web.controller.AnonymousController;
import net.microfalx.bootstrap.web.controller.PageController;
import net.microfalx.bootstrap.web.util.ResponseEntityUtils;
import net.microfalx.resource.Resource;
import org.slf4j.event.Level;
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
@Slf4j
public class ReportController extends PageController implements AnonymousController {

    private static final String HEADER_X_FRAME_OPTIONS = "X-Frame-Options";
    private static final String HEADER_CSP = "Content-Security-Policy";
    private static final String ALLOW_ALL_FRAMES = "frame-ancestors *";

    @Autowired private ReportService reportService;
    @Autowired private ApplicationService applicationService;

    @GetMapping(value = "", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<StreamingResponseBody> home(@RequestParam(value = "dynamic", defaultValue = "true") boolean dynamic,
                                                      @RequestParam(value = "secure", defaultValue = "false") boolean secure,
                                                      @RequestParam(value = "token", defaultValue = "false") String token) {
        try {
            checkToken(token);
            Report report = createReport().setDynamic(dynamic).setSecure(secure);
            Resource reportBody = Resource.temporary("report", "html");
            report.render(reportBody);
            return streamReport(report, reportBody);
        } catch (Exception e) {
            return streamError("Error generating report", e);
        }
    }

    @GetMapping(value = "{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<StreamingResponseBody> fragment(@PathVariable("id") String id,
                                                          @RequestParam(value = "dynamic", defaultValue = "true") boolean dynamic,
                                                          @RequestParam(value = "secure", defaultValue = "false") boolean secure,
                                                          @RequestParam(value = "token", defaultValue = "false") String token) {
        try {
            checkToken(token);
            Report report = createReport();
            report.setFragment(id).setDynamic(dynamic).setSecure(secure);
            Resource reportBody = Resource.temporary("report_export", ".html");
            report.render(reportBody);
            return streamReport(report, reportBody);
        } catch (Exception e) {
            return streamError("Error generating report fragment '" + id + "'", e);
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
                .header(HEADER_X_FRAME_OPTIONS, "ALLOWALL")
                .header(HEADER_CSP, ALLOW_ALL_FRAMES)
                .body(responseBody);
    }

    private ResponseEntity<StreamingResponseBody> streamError(String message, Exception e) {
        String body = message + ", reason: " + ResponseEntityUtils.getErrorMessage(e, false);
        Level level = Failure.of(e).getType().isSecurity() ? Level.DEBUG : Level.WARN;
        LOGGER.atLevel(level).log(body);
        StreamingResponseBody responseBody = outputStream -> outputStream.write(body.getBytes(UTF_8));
        return ResponseEntityUtils.fromFailure(Failure.of(e))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .header(HEADER_X_FRAME_OPTIONS, "ALLOWALL")
                .header(HEADER_CSP, ALLOW_ALL_FRAMES)
                .body(responseBody);
    }

    private void checkToken(String token) {
        // admins can access the report without a token
        if (SecurityContext.get().hasRole("admin")) return;
        // everybody else needs a valid secret key
        if (!reportService.isValid(token)) {
            throw new SecurityException("Invalid secret key provided");
        }
    }

    private Report createReport() {
        Report report = reportService.createReport();
        Theme theme = applicationService.getCurrentTheme();
        report.setTheme(theme.isLight() ? Report.Theme.LIGHT : Report.Theme.DARK);
        return report;
    }
}
