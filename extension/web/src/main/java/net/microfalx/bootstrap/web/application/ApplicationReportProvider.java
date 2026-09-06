package net.microfalx.bootstrap.web.application;

import net.microfalx.argus.report.Fragment;
import net.microfalx.argus.report.Template;
import net.microfalx.bootstrap.support.report.ApplicationFragmentProvider;
import net.microfalx.lang.annotation.Provider;

@Provider
public class ApplicationReportProvider extends ApplicationFragmentProvider {

    @Override
    public Fragment create() {
        return Fragment.builder("Application").template("application")
                .visible(false).icon("fa-solid fa-application")
                .build();
    }

    @Override
    public void update(Template template) {
        ApplicationService applicationService = getBean(ApplicationService.class);
        template.addVariable(Template.APPLICATION_VARIABLE, applicationService.getApplication());
    }
}
