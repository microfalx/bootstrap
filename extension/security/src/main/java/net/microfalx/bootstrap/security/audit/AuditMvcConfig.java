package net.microfalx.bootstrap.security.audit;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.microfalx.bootstrap.security.user.UserService;
import net.microfalx.bootstrap.web.util.HttpServletUtils;
import net.microfalx.bootstrap.web.util.PathFilter;
import net.microfalx.lang.StringUtils;
import net.microfalx.lang.annotation.Action;
import net.microfalx.lang.annotation.Module;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.reflect.Method;

import static net.microfalx.lang.StringUtils.isEmpty;
import static net.microfalx.lang.StringUtils.replaceFirst;

@Configuration
public class AuditMvcConfig implements WebMvcConfigurer {

    private final PathFilter pathFilter = new PathFilter();

    @Autowired
    private UserService userService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new Interceptor());
    }

    private void audit(HttpServletRequest request, HttpServletResponse response, Method method) {
        if (pathFilter.shouldExclude(request)) return;
        AuditContext context = AuditContext.get();
        context.setReference(request.getRequestURI());
        context.setErrorCode(Integer.toString(response.getStatus()));
        context.setClientInfo(HttpServletUtils.getClientIp(request));
        if (isEmpty(context.getAction())) context.setAction(getAction(method));
        if (isEmpty(context.getModule())) context.setModule(getModule(method));
        userService.audit(context);
    }

    private String getAction(Method method) {
        String action = StringUtils.capitalize(method.getName());
        Action nameAnnot = method.getAnnotation(Action.class);
        if (nameAnnot != null) action = nameAnnot.value();
        Operation operationAnnot = method.getAnnotation(Operation.class);
        if (operationAnnot != null) action = operationAnnot.summary();
        action = StringUtils.defaultIfEmpty(action, AuditContext.ACTION_OPEN);
        return action;
    }

    private String getModule(Method method) {
        String module = method.getDeclaringClass().getSimpleName();
        module = replaceFirst(module, "Controller", StringUtils.EMPTY_STRING);
        Module nameAnnot = method.getAnnotation(Module.class);
        if (nameAnnot != null) module = nameAnnot.value();
        return module;
    }

    private class Interceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            MDC.put("ClientIP", HttpServletUtils.getClientIp(request));
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
            if (handler instanceof HandlerMethod) {
                Method method = ((HandlerMethod) handler).getMethod();
                audit(request, response, method);
            }
        }


    }
}
