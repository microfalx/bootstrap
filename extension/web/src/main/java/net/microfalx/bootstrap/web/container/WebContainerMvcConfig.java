package net.microfalx.bootstrap.web.container;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.microfalx.bootstrap.dataset.formatter.FormatterUtils;
import net.microfalx.bootstrap.web.util.PathFilter;
import net.microfalx.metrics.Metrics;
import net.microfalx.metrics.Summary;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
public class WebContainerMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestInterceptor());
    }

    private static final ThreadLocal<Long> START_TIME = new ThreadLocal<>();
    private static final Metrics METRICS = Metrics.of("Web Container").withGroup("Requests");

    @Order
    private static class RequestInterceptor implements AsyncHandlerInterceptor {

        private final PathFilter pathFilter = new PathFilter();

        @SuppressWarnings("ConstantValue")
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            START_TIME.set(System.nanoTime());
            WebContainerRequest.REQUEST.set(request);
            WebContainerRequest containerRequest = WebContainerRequest.get();
            if (!response.isCommitted()) {
                FormatterUtils.setTimeZone(containerRequest.getTimeZone());
                FormatterUtils.setLocale(containerRequest.getLocale());
            }
            return true;
        }

        @Override
        public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            clear();
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
            if (pathFilter.shouldInclude(request)) recordTime(request);
            clear();
        }

        private void recordTime(HttpServletRequest request) {
            String matchedPattern = PathFilter.getRequestPattern(request);
            long duration = System.nanoTime() - START_TIME.get();
            Summary summary = METRICS.getSummary(matchedPattern);
            summary.record(Duration.ofNanos(duration));
        }

        private void clear() {
            FormatterUtils.setTimeZone(null);
            FormatterUtils.setLocale(null);
            WebContainerRequest.REQUEST.remove();
        }


    }
}
