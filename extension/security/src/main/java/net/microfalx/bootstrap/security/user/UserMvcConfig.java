package net.microfalx.bootstrap.security.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.microfalx.bootstrap.security.SecurityContext;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static net.microfalx.bootstrap.web.application.ApplicationUtils.getShortId;

@Configuration
public class UserMvcConfig implements WebMvcConfigurer {

    private static final int MAX_USER_ID_LENGTH = -10;

    @Autowired
    private UserService userService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserInterceptor());
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    private class UserInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            SecurityContext securityContext = userService.getCurrentSecurityContext();
            SecurityContextImpl.CONTEXT.set(securityContext);
            MDC.put("UserId", getShortId(securityContext.getUser().getUsername(), MAX_USER_ID_LENGTH));
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
            SecurityContextImpl.CONTEXT.remove();
        }
    }
}
