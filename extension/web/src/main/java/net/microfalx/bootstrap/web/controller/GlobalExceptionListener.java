package net.microfalx.bootstrap.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.microfalx.bootstrap.core.utils.Failure;
import net.microfalx.bootstrap.support.report.Issue;
import net.microfalx.bootstrap.web.util.HttpServletUtils;
import net.microfalx.bootstrap.web.util.PathFilter;
import net.microfalx.lang.ClassUtils;
import net.microfalx.metrics.Metrics;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionListener implements HandlerExceptionResolver {

    private static final Map<Failure.Type, Issue.Severity> FAILURE_SEVERITIES = new HashMap<>();
    private static final PathFilter DEFAULT_EXCLUSIONS = PathFilter.all();

    private static final Metrics FAILURE = Metrics.of("Bootstrap").withGroup("Controller Failures");
    private static final Metrics FAILURE_BY_EXCEPTION = FAILURE.withGroup("Exception");
    private static final Metrics FAILURE_BY_PATH = FAILURE.withGroup("Path");

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        FAILURE_BY_EXCEPTION.count(ClassUtils.getCompactName(exception));
        FAILURE_BY_PATH.count(PathFilter.getRootPath(request));
        if (shouldIgnore(request, exception)) return null;
        String matchedPattern = PathFilter.getRequestPattern(request);
        Issue.Severity severity = getSeverityFromException(request, exception);
        if (severity != null) {
            Issue.create(Issue.Type.STABILITY, matchedPattern).withDescription(exception, "Unhandled exception in controller")
                    .withModule("Controller").withSeverity(severity).withAttributeCounter(PathFilter.getRootPath(request))
                    .register();
        }
        return null;
    }

    private boolean shouldIgnore(HttpServletRequest request, Exception exception) {
        boolean ignore = DEFAULT_EXCLUSIONS.shouldExclude(request);
        if (!ignore) ignore = HttpServletUtils.shouldIgnoreFailure(request, exception);
        return ignore;
    }

    private Issue.Severity getSeverityFromException(HttpServletRequest request, Exception exception) {
        Failure.Type type = Failure.getType(exception);
        Issue.Severity severity = FAILURE_SEVERITIES.get(type);
        if (severity == null) severity = Issue.Severity.HIGH;
        return severity;
    }

    static {
        FAILURE_SEVERITIES.put(Failure.Type.AUTHORIZATION, Issue.Severity.HIGH);
        FAILURE_SEVERITIES.put(Failure.Type.AUTHENTICATION, Issue.Severity.HIGH);
        FAILURE_SEVERITIES.put(Failure.Type.ILLEGAL_INPUT, Issue.Severity.LOW);
        FAILURE_SEVERITIES.put(Failure.Type.ILLEGAL_OUTPUT, Issue.Severity.LOW);
        FAILURE_SEVERITIES.put(Failure.Type.CONNECTIVITY, Issue.Severity.LOW);
        FAILURE_SEVERITIES.put(Failure.Type.NETWORK, Issue.Severity.LOW);
        FAILURE_SEVERITIES.put(Failure.Type.TIMED_OUT, Issue.Severity.LOW);
        FAILURE_SEVERITIES.put(Failure.Type.RESOURCE_NOT_FOUND, Issue.Severity.LOW);
        FAILURE_SEVERITIES.put(Failure.Type.SERVICE_UNAVAILABLE, Issue.Severity.HIGH);
        FAILURE_SEVERITIES.put(Failure.Type.CONFIGURATION, Issue.Severity.CRITICAL);
    }
}
