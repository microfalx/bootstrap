package net.microfalx.bootstrap.web.application;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import net.microfalx.bootstrap.core.utils.Json;
import net.microfalx.bootstrap.web.application.annotation.SystemTheme;
import net.microfalx.lang.AnnotationUtils;
import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ExceptionUtils.rethrowException;
import static net.microfalx.lang.StringUtils.*;

/**
 * An MVC interceptor which handles the application related interactions.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApplicationRequestInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationRequestInterceptor.class);

    private static final String THEME_QUERY_PARAMETER = "_theme";
    private static final String THEME_MODE_QUERY_PARAMETER = "_theme_mode";
    private static final String THEME_SESSION_ATTR = "$APPLICATION_THEME$";
    private static final String THEME_SESSION_MODE_ATTR = "$APPLICATION_THEME_MODE$";
    private static final String THEME_SESSION_DOMAIN_APPLIED_ATTR = "$APPLICATION_THEME_DOMAIN_APPLIED$";

    private final ApplicationService applicationService;
    private final Application application;

    ApplicationRequestInterceptor(ApplicationService applicationService, Application application) {
        requireNonNull(applicationService);
        requireNonNull(applicationService);
        this.applicationService = applicationService;
        this.application = application;
    }

    private ThemeInfo handleThemeFromRequest(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String themeId = HttpMethod.valueOf(request.getMethod()) == HttpMethod.GET ? request.getParameter(THEME_QUERY_PARAMETER) : null;
        String themeMode = HttpMethod.valueOf(request.getMethod()) == HttpMethod.GET ? request.getParameter(THEME_MODE_QUERY_PARAMETER) : null;
        if (session != null) {
            // if we have overrides from request, store them in the session
            storeThemeInSession(request, session, themeId, themeMode);
            themeId = (String) session.getAttribute(THEME_SESSION_ATTR);
            themeMode = (String) session.getAttribute(THEME_SESSION_MODE_ATTR);
            Boolean perDomainThemeApplied = (Boolean) session.getAttribute(THEME_SESSION_DOMAIN_APPLIED_ATTR);
            String host = request.getServerName();
            // select the theme based on domain
            if (isEmpty(themeId) && isNotEmpty(host) && perDomainThemeApplied == null) {
                Optional<Theme> themeForDomain = applicationService.getThemeForDomain(host);
                if (themeForDomain.isPresent()) {
                    themeId = themeForDomain.get().getId();
                    LOGGER.debug("Applying theme '{}' for domain '{}'", themeForDomain.get().getId(), host);
                    session.setAttribute(THEME_SESSION_ATTR, themeId);
                    session.setAttribute(THEME_SESSION_DOMAIN_APPLIED_ATTR, Boolean.TRUE);
                }
            }
        }
        return new ThemeInfo(themeId, themeMode);
    }

    private void storeThemeInSession(HttpServletRequest request, HttpSession session, String themeId, String themeMode) {
        if (isNotEmpty(themeId)) {
            LOGGER.debug("Applying theme '{}' for request '{}'", themeId, request.getRequestURI());
            session.setAttribute(THEME_SESSION_ATTR, themeId);
        }
        if (isNotEmpty(themeMode)) {
            LOGGER.debug("Applying theme mode '{}' for request '{}'", themeId, request.getRequestURI());
            session.setAttribute(THEME_SESSION_MODE_ATTR, themeMode);
        }
    }

    private ThemeInfo handleThemeFromHandler(Object handler, ThemeInfo themeInfo) {
        Class<?> handlerClass = null;
        if (handler instanceof HandlerMethod) handlerClass = ((HandlerMethod) handler).getBeanType();
        if (handlerClass == null) return themeInfo;
        String themeId = themeInfo.getId();
        String themeMode = themeInfo.getMode();
        net.microfalx.bootstrap.web.application.annotation.Theme themeAnnot = AnnotationUtils.getAnnotation(handlerClass, net.microfalx.bootstrap.web.application.annotation.Theme.class);
        if (themeAnnot == null) {
            SystemTheme systemThemeAnnot = AnnotationUtils.getAnnotation(handlerClass, SystemTheme.class);
            // if the controller asks for the system theme and there is no external theme selected, pick the system theme
            if (systemThemeAnnot != null && StringUtils.isEmpty(themeId)) {
                themeId = application.getSystemTheme().getId();
            }
        } else {
            themeId = themeAnnot.value();
            themeMode = themeAnnot.mode().name().toLowerCase();
        }
        return new ThemeInfo(themeId, themeMode);
    }

    private void handleTheme(HttpServletRequest request, Object handler) {
        ThemeInfo themeInfo = handleThemeFromRequest(request);
        if (isEmpty(themeInfo.id)) themeInfo = handleThemeFromHandler(handler, themeInfo);
        if (isEmpty(themeInfo.id)) {
            themeInfo = new ThemeInfo(applicationService.getApplication().getTheme().getId(), themeInfo.getMode());
        }
        try {
            net.microfalx.bootstrap.web.application.Theme theme = applicationService.getTheme(themeInfo.getId());
            if (isNotEmpty(themeInfo.getMode())) {
                theme = theme.withMode(net.microfalx.bootstrap.web.application.Theme.Mode.of(themeInfo.getMode()));
            }
            Theme.set(theme);
        } catch (ApplicationException e) {
            LOGGER.warn("A theme with identifier '{}' is not registered", themeInfo);
        } catch (Exception e) {
            LOGGER.atError().setCause(e).log("Failed to extract theme with identifier '{}' from controller '{}'",
                    themeInfo, ClassUtils.getName(handler));
        }
    }

    private void handleLocalStorage(HttpServletRequest request) {
        String json = request.getHeader("X-Application-LocalState");
        if (isNotEmpty(json)) {
            LOGGER.debug("Received local storage state from client {}, entries {}", json, request.getRequestURI());
            try {
                Json.asMap(json).forEach(LocalStorage::set);
            } catch (IOException e) {
                rethrowException(e);
            }
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        handleTheme(request, handler);
        handleLocalStorage(request);
        Application.set(defaultIfEmpty(request.getHeader("X-Application-Id"), "na"));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        Theme.clear();
        Application.clear();
        AssetBundle.clear();
        LocalStorage.clear();
    }

    @Getter
    @ToString
    @AllArgsConstructor
    private static class ThemeInfo {

        private final String id;
        private final String mode;

    }
}
