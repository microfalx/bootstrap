package net.microfalx.bootstrap.dos;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.microfalx.bootstrap.web.util.HttpServletUtils;
import net.microfalx.bootstrap.web.util.PathFilter;
import net.microfalx.lang.ExceptionUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import static net.microfalx.bootstrap.dos.DosUtils.ERROR;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * Handles DoS validation by delegating to {@link DosValidator}.
 */
public class DosHandlerInterceptor implements HandlerInterceptor {

    private final static String DOS_VALIDATOR_ATTRIBUTE = "$BOOTSTRAP_DOS_VALIDATOR$";

    private final DosService dosService;
    private final PathFilter pathFilter = PathFilter.all();

    public DosHandlerInterceptor(DosService dosService) {
        requireNonNull(dosService);
        this.dosService = dosService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        DosValidator validator = getValidator(request, response);
        boolean continueProcessing = true;
        if (validator != null && pathFilter.shouldInclude(request)) continueProcessing = validator.preHandle();
        return continueProcessing;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) throws Exception {
        DosValidator validator = (DosValidator) request.getAttribute(DOS_VALIDATOR_ATTRIBUTE);
        if (validator != null && shouldInclude(request, exception)) {
            validator.afterCompletion(response, exception);
        }
    }

    private boolean shouldInclude(HttpServletRequest request, Exception exception) {
        // if a failure, ignore unless it goes above a given threshold
        if (HttpServletUtils.shouldIgnoreFailure(request, exception)) return false;
        // follow the rest of the rules
        return pathFilter.shouldInclude(request);
    }

    private DosValidator getValidator(HttpServletRequest request, HttpServletResponse response) {
        DosValidator validator = (DosValidator) request.getAttribute(DOS_VALIDATOR_ATTRIBUTE);
        if (validator != null) return validator;
        try {
            validator = new DosValidator(dosService, request, response);
            request.setAttribute(DOS_VALIDATOR_ATTRIBUTE, validator);
            return validator;
        } catch (Exception e) {
            ERROR.increment(ExceptionUtils.getRootCauseName(e));
            return null;
        }
    }

}
