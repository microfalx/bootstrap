package net.microfalx.bootstrap.support.report;

import net.microfalx.argus.api.Issue;
import net.microfalx.argus.report.Template;
import net.microfalx.resource.Resource;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * An interface for reporting listeners.
 */
public interface ReportingListener {

    /**
     * Updates the template before rendering.
     *
     * @param template the template
     */
    default void update(Template template) {
        // empty by default, not all listeners need to update the template
    }

    /**
     * Returns the email destinations which will receive an email about the system reports.
     *
     * @return a non-null instance
     */
    default Set<String> getDestinations() {
        return Collections.emptySet();
    }

    /**
     * Returns the issues of the application.
     *
     * @return a non-null instance
     */
    default Collection<Issue> getIssues() {
        return Collections.emptyList();
    }

    /**
     * Sends the report via email.
     *
     * @param destinations the email destinations
     * @param title        the email title
     * @param summary      the summary resource
     * @param attachment   the optional attachment resource
     * @return {@code true} if the report has been sent, {@code false} otherwise
     */
    default boolean send(Set<String> destinations, String title, Resource summary,
                         Optional<Resource> attachment) {
        return false;
    }
}
