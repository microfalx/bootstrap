package net.microfalx.bootstrap.web.util;

import jakarta.servlet.http.HttpServletRequest;
import net.microfalx.bootstrap.core.utils.Failure;
import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.Identifiable;
import net.microfalx.lang.TimeUtils;
import org.eclipse.jetty.ee10.servlet.QuietServletException;
import org.eclipse.jetty.io.EofException;
import org.springframework.web.HttpMediaTypeException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.System.currentTimeMillis;

/**
 * Various utilities related to HttpServletRequest and HttpServletResponse.
 */
public class HttpServletUtils extends net.microfalx.lang.HttpServletUtils {

    private static final int DEFAULT_ERROR_THRESHOLD = 3;

    private static final Map<String, RequestMetrics> requestErrors = new ConcurrentHashMap<>();

    /**
     * Tracks failures by client and failure type and decided when a failure should start being reported.
     *
     * @param request   the HTTP request
     * @param throwable the exception
     * @return {@code true} if should ignore failure, {@code false} otherwise
     */
    public static boolean shouldIgnoreFailure(HttpServletRequest request, Throwable throwable) {
        if (throwable == null) return false;
        String id = getClientIp(request) + ":" + ClassUtils.getName(throwable);
        RequestMetrics metrics = requestErrors.computeIfAbsent(id, RequestMetrics::new);
        return metrics.touch() < DEFAULT_ERROR_THRESHOLD;
    }

    /**
     * Cleanups stale failures
     */
    public static void cleanupFailures() {
        for (RequestMetrics metrics : requestErrors.values()) {
            if (metrics.isExpired()) requestErrors.remove(metrics.getId());
        }
    }

    public static void init() {
        // do nothing, used to warmup statics
    }

    static {
        Failure.registerType(Failure.Type.RESOURCE_NOT_FOUND, NoResourceFoundException.class);
        Failure.registerType(Failure.Type.RESOURCE_NOT_FOUND, NoHandlerFoundException.class);
        Failure.registerType(Failure.Type.RESET, QuietServletException.class);
        Failure.registerType(Failure.Type.RESET, EofException.class);
        Failure.registerSubType(Failure.Type.ILLEGAL_INPUT, HttpMediaTypeException.class);
    }


    private static class RequestMetrics implements Identifiable<String> {

        private final String id;
        private final AtomicInteger errorCount = new AtomicInteger(0);
        private volatile long lastUpdate = currentTimeMillis();

        RequestMetrics(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        private int touch() {
            lastUpdate = currentTimeMillis();
            return errorCount.incrementAndGet();
        }

        private boolean isExpired() {
            return TimeUtils.millisSince(lastUpdate) > TimeUtils.FIVE_MINUTE;
        }
    }
}
