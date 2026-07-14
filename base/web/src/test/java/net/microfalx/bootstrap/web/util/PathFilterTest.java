package net.microfalx.bootstrap.web.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class PathFilterTest {

    @Mock
    private HttpServletRequest httpServletRequest;
    private String requestUri = "/";

    private final PathFilter filter = new PathFilter(true, true, true);

    @BeforeEach
    void setup() {
        when(httpServletRequest.getRequestURI()).thenAnswer(invocation -> requestUri);
    }

    @Test
    void getRootPath() {
        assertEquals("/", PathFilter.getRootPath(httpServletRequest));
        requestUri = "/a";
        assertEquals("/a", PathFilter.getRootPath(httpServletRequest));
        requestUri = "/a/b";
        assertEquals("/a", PathFilter.getRootPath(httpServletRequest));
        requestUri = "/a/b/c";
        assertEquals("/a/b", PathFilter.getRootPath(httpServletRequest));
        requestUri = "/a/b/c/d";
        assertEquals("/a/b", PathFilter.getRootPath(httpServletRequest));
    }

    @Test
    void isExcluded() {
        assertExcluded("/dragonshell.php");
        assertExcluded("/profile/wen.php");
        assertExcluded("/win.asp");
        assertExcluded("/.env.production");
        assertExcluded("/services/.env");
        assertExcluded("/.env.old");
        assertExcluded("/test.md");
        assertExcluded("/test.sql");
    }

    private void assertExcluded(String path) {
        requestUri = path;
        assertTrue(filter.shouldExclude(httpServletRequest));
    }

}