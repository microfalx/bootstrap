package net.microfalx.bootstrap.security.provisioning;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.bootstrap.restapi.RestApiAccessDeniedHandler;
import net.microfalx.bootstrap.restapi.RestApiAuthenticationEntryPoint;
import net.microfalx.bootstrap.web.util.HttpServletUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.RememberMeServices;

import static net.microfalx.lang.CollectionUtils.setToString;
import static net.microfalx.lang.StringUtils.addEndSlash;
import static net.microfalx.lang.StringUtils.addStartSlash;
import static org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Autowired
    private SecurityProperties securityProperties;


    @Bean
    @Order(10)
    public SecurityFilterChain webChain(HttpSecurity httpSecurity, RememberMeServices rememberMeServices) throws Exception {
        if (securityProperties.isEnabled()) {
            httpSecurity.securityMatcher(addMatchAll("/"));
            httpSecurity.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/**").access((authentication, context) -> {
                        boolean isLocal = HttpServletUtils.isClientLocal(context.getRequest());
                        return new org.springframework.security.authorization.AuthorizationDecision(isLocal);
                    })
                    .anyRequest().permitAll()
            );
            updateLogin(httpSecurity);
            updateRememberMe(httpSecurity, rememberMeServices);
            updateCommon(httpSecurity);
            updateExceptionHandling(httpSecurity);
        } else {
            httpSecurity.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            updateCommon(httpSecurity);
        }
        return httpSecurity.build();
    }

    private void updateCommon(HttpSecurity httpSecurity) throws Exception {
        updateSessionManagement(httpSecurity);
        updateOther(httpSecurity);
        updateHeaders(httpSecurity);
        updateAnonymous(httpSecurity);
    }

    private void updateRememberMe(HttpSecurity httpSecurity, RememberMeServices rememberMeServices) throws Exception {
        httpSecurity.rememberMe(rememberMe -> rememberMe.rememberMeServices(rememberMeServices));
    }

    private void updateSessionManagement(HttpSecurity httpSecurity) throws Exception {
        httpSecurity.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.ALWAYS));
    }

    private void updateOther(HttpSecurity httpSecurity) throws Exception {
        if (securityProperties.isCsrf()) {
            httpSecurity.csrf(csrf -> {
                for (String disablePath : securityProperties.getCsrfDisable()) {
                    disablePath = addMatchAll(disablePath);
                    LOGGER.info("CSRF protection is disabled for path '{}'", disablePath);
                    csrf.ignoringRequestMatchers(disablePath);
                }
            });
        } else {
            httpSecurity.csrf(CsrfConfigurer::disable);
        }
        if (!securityProperties.isCsrf()) {
            LOGGER.info("CSRF protection is disabled");
        } else {
            LOGGER.info("CSRF protection is enabled with the exception of: {}", setToString(securityProperties.getCsrfDisable()));
        }
    }

    private void updateHeaders(HttpSecurity httpSecurity) throws Exception {
        httpSecurity.headers(headers -> {
            if (securityProperties.isXfo()) LOGGER.info("XFrame protection is disabled");
            headers.frameOptions(securityProperties.isXfo() ? HeadersConfigurer.FrameOptionsConfig::disable : HeadersConfigurer.FrameOptionsConfig::sameOrigin);
            headers.xssProtection(HeadersConfigurer.XXssConfig::disable);
            headers.referrerPolicy(rp -> rp.policy(NO_REFERRER));
        });
    }

    private void updateAnonymous(HttpSecurity httpSecurity) throws Exception {
        httpSecurity.anonymous(a -> a.principal("guest"));
    }

    private void updateExceptionHandling(HttpSecurity httpSecurity) throws Exception {
        var jsonEntryPoint = new RestApiAuthenticationEntryPoint();
        var jsonDenied = new RestApiAccessDeniedHandler();
        // Match requests that accept application/json
        var contentNegotiation = httpSecurity.getSharedObject(org.springframework.web.accept.ContentNegotiationStrategy.class);
        var jsonMatcher = new org.springframework.security.web.util.matcher.MediaTypeRequestMatcher(
                contentNegotiation, org.springframework.http.MediaType.APPLICATION_JSON);
        jsonMatcher.setIgnoredMediaTypes(java.util.Set.of(org.springframework.http.MediaType.ALL));
        // For JSON-accepting requests, send JSON 401/403 instead of redirect
        httpSecurity.exceptionHandling(e -> e
                .defaultAuthenticationEntryPointFor(jsonEntryPoint, jsonMatcher)
                .defaultAccessDeniedHandlerFor(jsonDenied, jsonMatcher)
        );
    }

    private void updateLogin(HttpSecurity httpSecurity) throws Exception {
        httpSecurity.formLogin(login -> login.loginPage("/login").loginProcessingUrl("/login/auth")
                .usernameParameter("username").passwordParameter("password").permitAll());
        httpSecurity.logout(logout -> logout.clearAuthentication(true)
                .invalidateHttpSession(true).logoutUrl("/logout")
                .logoutSuccessUrl("/"));
    }

    private String addMatchAll(String path) {
        String result = addStartSlash(addEndSlash(path));
        return result + "**";
    }


}
