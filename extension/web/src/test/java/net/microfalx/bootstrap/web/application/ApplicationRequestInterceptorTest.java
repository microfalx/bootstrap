package net.microfalx.bootstrap.web.application;

import net.microfalx.lang.ReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApplicationRequestInterceptorTest {

    @Mock private Application application;
    @Mock private ApplicationService applicationService;

    @InjectMocks ApplicationRequestInterceptor applicationInterceptor;

    private Theme defaultTheme;
    private Theme systemTheme;
    private Map<String, Theme> themes = new HashMap<>();
    private MockHttpServletRequest request = new MockHttpServletRequest("GET", "/request");
    private MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void before() throws Exception {
        // make sure we have a session
        request.getSession(true);
        Theme.clear();
        defaultTheme = Theme.builder("default").build();
        themes.put("default", defaultTheme);
        Theme customTheme = Theme.builder("light").mode(Theme.Mode.LIGHT).build();
        themes.put(customTheme.getId(), customTheme);
        customTheme = Theme.builder("dark").mode(Theme.Mode.DARK).build();
        themes.put(customTheme.getId(), customTheme);
        systemTheme = Theme.builder("system").mode(Theme.Mode.AUTO).build();
        themes.put(systemTheme.getId(), systemTheme);

        when(application.getTheme()).thenReturn(defaultTheme);
        when(application.getSystemTheme()).thenReturn(systemTheme);

        when(applicationService.getApplication()).thenReturn(application);
        when(applicationService.getTheme(any())).then(invocation -> themes.get((String) invocation.getArgument(0)));

        applicationService.afterPropertiesSet();
    }

    @AfterEach
    void after() throws Exception {
        if (applicationInterceptor != null) {
            applicationInterceptor.afterCompletion(request, response, this, null);
        }
    }

    @Test
    void noExternalTheme() throws Exception {
        applicationInterceptor.preHandle(request, response, getHandler(this));
        assertEquals(defaultTheme, getCurrentTheme());
        assertEquals(Theme.Mode.AUTO, getCurrentTheme().getMode());
    }

    @Test
    void onlyExternalThemeMode() throws Exception {
        request.addParameter("_theme_mode", "light");
        applicationInterceptor.preHandle(request, response, this);
        assertEquals("default", getCurrentTheme().getId());
        assertEquals(Theme.Mode.LIGHT, getCurrentTheme().getMode());
    }

    @Test
    void onlyExternalTheme() throws Exception {
        request.addParameter("_theme", "light");
        applicationInterceptor.preHandle(request, response, this);
        assertEquals("light", getCurrentTheme().getId());
        assertEquals(Theme.Mode.LIGHT, getCurrentTheme().getMode());
    }

    @Test
    void externalThemeAndMode() throws Exception {
        request.addParameter("_theme", "light");
        request.addParameter("_theme_mode", "dark");
        applicationInterceptor.preHandle(request, response, this);
        assertEquals("light", getCurrentTheme().getId());
        assertEquals(Theme.Mode.DARK, getCurrentTheme().getMode());
    }

    @Test
    void themeAnnotationWithNoOverride() throws Exception {
        applicationInterceptor.preHandle(request, response, getHandler(new WithTheme()));
        assertEquals("light", getCurrentTheme().getId());
        assertEquals(Theme.Mode.DARK, getCurrentTheme().getMode());
    }

    @Test
    void themeAnnotationWithOverride() throws Exception {
        request.addParameter("_theme", "dark");
        applicationInterceptor.preHandle(request, response, getHandler(new WithTheme()));
        assertEquals("dark", getCurrentTheme().getId());
        assertEquals(Theme.Mode.DARK, getCurrentTheme().getMode());
    }

    @Test
    void systemThemeAnnotationWithNoOverride() throws Exception {
        applicationInterceptor.preHandle(request, response, getHandler(new WithSystemTheme()));
        assertEquals("system", getCurrentTheme().getId());
        assertEquals(Theme.Mode.AUTO, getCurrentTheme().getMode());
    }

    @Test
    void systemThemeAnnotationWithOverride() throws Exception {
        request.addParameter("_theme", "dark");
        applicationInterceptor.preHandle(request, response, getHandler(new WithSystemTheme()));
        assertEquals("dark", getCurrentTheme().getId());
        assertEquals(Theme.Mode.DARK, getCurrentTheme().getMode());
    }

    @Test
    void themeForDomain() throws Exception {
        when(applicationService.getThemeForDomain(eq("localhost"))).thenReturn(Optional.of(systemTheme));
        applicationInterceptor.preHandle(request, response, getHandler(this));
        assertEquals(systemTheme, getCurrentTheme());
    }

    private HandlerMethod getHandler(Object object) {
        Method method = ReflectionUtils.getMethods(object.getClass()).iterator().next();
        return new HandlerMethod(object, method);
    }

    private static Theme getCurrentTheme() {
        return Theme.get().orElse(null);
    }

    @net.microfalx.bootstrap.web.application.annotation.Theme(value = "light", mode = net.microfalx.bootstrap.web.application.annotation.Theme.Mode.DARK)
    private static class WithTheme {
        void test() {

        }
    }

    @net.microfalx.bootstrap.web.application.annotation.SystemTheme
    private static class WithSystemTheme {
        void test() {

        }
    }

}