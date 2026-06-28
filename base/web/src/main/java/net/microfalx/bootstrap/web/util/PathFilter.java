package net.microfalx.bootstrap.web.util;

import jakarta.servlet.http.HttpServletRequest;
import net.microfalx.lang.StringUtils;
import net.microfalx.lang.UriUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.StringUtils.*;
import static net.microfalx.lang.UriUtils.SLASH;

/**
 * A utility class which filters paths based on exclusion rules.
 * <p>
 * The filter accepts path prefix (everything starting with) and Ant patterns.
 */
public class PathFilter {

    private final Set<String> excludedPaths = new CopyOnWriteArraySet<>();
    private final Set<String> excludedPatterns = new CopyOnWriteArraySet<>();

    public PathFilter() {
        this(true);
    }

    public PathFilter(boolean withDefaults) {
        this(withDefaults, false);
    }

    public PathFilter(boolean withDefaults, boolean withNonJavaHandler) {
        if (withDefaults) registerDefaultPaths();
        if (withNonJavaHandler) registerDefaultNonJavaPatterns();
    }

    public boolean isRoot(HttpServletRequest request) {
        return UriUtils.isRoot(request.getRequestURI());
    }

    /**
     * Registers a path prefix for exclusions (basically /path/**).
     *
     * @param path the path
     */
    public void registerExclusionPathPrefix(String path) {
        requireNonNull(path);
        excludedPaths.add(removeStartSlash(path).toLowerCase());
    }

    /**
     * Registers a path pattern for exclusion.
     * <p>
     * The pattern is an Ant pattern
     *
     * @param pattern the pattern
     */
    public void registerExclusionPattern(String pattern) {
        requireNonNull(pattern);
        excludedPatterns.add(removeStartSlash(pattern).toLowerCase());
    }

    /**
     * Returns whether the request should be excluded.
     *
     * @param request the HTTP request
     * @return {@code true} if excluded, {@code false} otherwise
     */
    public boolean shouldExclude(HttpServletRequest request) {
        String path = request.getRequestURI();
        return shouldExclude(path);
    }

    /**
     * Returns whether the request should be excluded.
     *
     * @param path the HTTP request path
     * @return {@code true} if excluded, {@code false} otherwise
     */
    public boolean shouldExclude(String path) {
        path = removeStartSlash(path).toLowerCase();
        for (String excludedPath : excludedPaths) {
            if (path.startsWith(excludedPath)) return true;
        }
        PathMatcher matcher = new AntPathMatcher();
        for (String ignorePattern : excludedPatterns) {
            if (matcher.match(ignorePattern, path)) return true;
        }
        return false;
    }

    /**
     * Returns whether the request should be included.
     *
     * @param request the HTTP request
     * @return {@code true} if included, {@code false} otherwise
     */
    public boolean shouldInclude(HttpServletRequest request) {
        String path = request.getRequestURI();
        return shouldInclude(path);
    }

    /**
     * Returns whether the request should be included.
     *
     * @param path the HTTP request path
     * @return {@code true} if included, {@code false} otherwise
     */
    public boolean shouldInclude(String path) {
        return !shouldExclude(path);
    }

    /**
     * Returns the request as a pattern.
     * <p>
     * If method checks the request object for the original controller matcher, otherwise it returns the root
     * of the request.
     *
     * @param request the request
     * @return the patter (or root path as a backup)
     */
    public static String getRequestPattern(HttpServletRequest request) {
        requireNonNull(request);
        String matchedPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (matchedPattern == null) matchedPattern = getRootPath(request.getRequestURI(), -1);
        return addStartSlash(matchedPattern);
    }

    /**
     * Returns the root path with the given number of parts.
     *
     * @param request the request
     * @return a non-null instance
     */
    public static String getRootPath(HttpServletRequest request) {
        return getRootPath(request, -1);
    }

    /**
     * Returns the root path with the given number of parts.
     *
     * @param request the request
     * @param parts   the number of parts
     * @return a non-null instance
     */
    public static String getRootPath(HttpServletRequest request, int parts) {
        requireNonNull(request);
        return getRootPath(request.getRequestURI(), parts);
    }

    private void registerDefaultPaths() {
        registerExclusionPathPrefix("ping");
        registerExclusionPathPrefix("status");
        registerExclusionPathPrefix("event");
        registerExclusionPathPrefix("asset");
        registerExclusionPathPrefix("image");
        registerExclusionPathPrefix("error");
        registerExclusionPathPrefix("favicon.ico");
        registerExclusionPathPrefix(".well-known");
    }

    private void registerDefaultNonJavaPatterns() {
        registerExclusionPattern("**/*.php*");
        registerExclusionPattern("**/*.phtml");
        registerExclusionPattern("**/*.asp");
        registerExclusionPattern("**/*.cgi");
        registerExclusionPattern("**/*.cfm");
        registerExclusionPattern("**/.env*");
        registerExclusionPattern("**/.git");
        registerExclusionPattern("**/wp-content*");
        registerExclusionPattern("**/wp-admin*");
        registerExclusionPattern("**/wp-includes*");
    }

    private static String getRootPath(String path, int parts) {
        String[] fragments = StringUtils.split(path, "/");
        if (fragments.length == 0) return SLASH;
        int finalParts = parts;
        if (finalParts <= 0) {
            // if in auto mode, if it is longer than 3
            if (fragments.length >= 3) {
                finalParts = 2;
            } else {
                finalParts = 1;
            }
        }
        return addStartSlash(defaultIfEmpty(StringUtils.join("/", Arrays.copyOf(fragments, finalParts)), SLASH));
    }
}
