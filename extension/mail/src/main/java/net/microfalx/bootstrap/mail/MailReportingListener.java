package net.microfalx.bootstrap.mail;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.bootstrap.core.utils.ApplicationContextSupport;
import net.microfalx.bootstrap.support.report.ReportingListener;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.resource.Resource;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

@Provider
@Slf4j
public class MailReportingListener extends ApplicationContextSupport implements ReportingListener {

    @Override
    public boolean send(Set<String> destinations, String title,
                        Resource summary, Resource fullReport,
                        Optional<Resource> attachment) {
        MailService mailService = getBean(MailService.class);
        for (String destination : destinations) {
            try {
                Collection<Resource> attachments = attachment.map(Collections::singleton).orElse(Collections.emptySet());
                mailService.send(destination, title, summary, attachments);
            } catch (Exception e) {
                LOGGER.warn("Failed to send report to {}", destination, e);
            }
        }
        return true;
    }
}
