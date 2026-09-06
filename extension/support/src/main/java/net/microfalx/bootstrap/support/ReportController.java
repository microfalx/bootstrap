package net.microfalx.bootstrap.support;

import jakarta.annotation.security.RolesAllowed;
import net.microfalx.argus.report.Report;
import net.microfalx.bootstrap.support.report.ReportService;
import net.microfalx.bootstrap.web.application.ApplicationService;
import net.microfalx.bootstrap.web.application.Theme;
import net.microfalx.bootstrap.web.application.annotation.SystemTheme;
import net.microfalx.bootstrap.web.controller.PageController;
import net.microfalx.resource.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping(value = "/support/report")
@SystemTheme
@RolesAllowed("admin")
public class ReportController extends PageController {

    @Autowired private ReportService reportService;
    @Autowired private ApplicationService applicationService;

    @GetMapping("")
    @ResponseBody
    public String home(@RequestParam(value = "dynamic", defaultValue = "true") boolean dynamic,
                       @RequestParam(value = "secure", defaultValue = "false") boolean secure) {
        Report report = createReport().setDynamic(dynamic).setSecure(secure);
        Resource reportBody = Resource.temporary("report", "html");
        try {
            report.render(reportBody);
            return reportBody.loadAsString();
        } catch (Exception e) {
            return "Error generating report: " + ExceptionUtils.getRootCauseMessage(e);
        }
    }

    @GetMapping("{id}")
    @ResponseBody
    public String fragment(@PathVariable("id") String id,
                           @RequestParam(value = "dynamic", defaultValue = "true") boolean dynamic,
                           @RequestParam(value = "secure", defaultValue = "false") boolean secure) {
        Report report = createReport();
        report.setFragment(id).setDynamic(dynamic).setSecure(secure);
        Resource reportBody = Resource.temporary("report_export", ".html");
        try {
            report.render(reportBody);
            return reportBody.loadAsString();
        } catch (Exception e) {
            return "Error generating report: " + ExceptionUtils.getRootCauseMessage(e);
        }
    }

    private Report createReport() {
        Report report = reportService.createReport();
        Theme theme = applicationService.getCurrentTheme();
        report.setTheme(theme.isLight() ? Report.Theme.LIGHT : Report.Theme.DARK);
        return report;
    }
}
