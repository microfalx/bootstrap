package net.microfalx.bootstrap.web.application;

import net.microfalx.lang.StringUtils;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;

import static net.microfalx.lang.StringUtils.NA_ID_STRING;

public final class ApplicationUtils {

    public static String NO_VERSION = "0.0.0";
    private static final int MAX_SESSION_ID_LENGTH = 5;

    /**
     * Returns an identifier which is a short version of the session id.
     *
     * @param id the full identifier
     * @return the short identifier, or a default value if the id is null
     * @see #getShortId(String, int)
     */
    public static String getShortId(String id) {
        return getShortId(id, MAX_SESSION_ID_LENGTH);
    }

    /**
     * Returns an identifier which is a short version of the session id.
     * The short version is prefixed with '*' do indicate there is something else before the short id.
     * <p>
     * if the length is positive, the short id is taken from the right, otherwise from the left.
     *
     * @param id     the full identifier
     * @param length the length of the short identifier
     * @return the short identifier, or a default value if the id is null
     */
    public static String getShortId(String id, int length) {
        if (StringUtils.isEmpty(id) || NA_ID_STRING.equals(id)) return NA_ID_STRING;
        if (length < 0) {
            return org.apache.commons.lang3.StringUtils.left(id, -length) + "*";
        } else {
            return "*" + org.apache.commons.lang3.StringUtils.right(id, length);
        }
    }

    /**
     * Returns a collection of URLs pointing to asset descriptors.
     *
     * @return a non-null collection;
     */
    static Collection<URL> getAssetDescriptors() throws IOException {
        Collection<URL> urls = new ArrayList<>();
        Enumeration<URL> resources = ApplicationUtils.class.getClassLoader().getResources("asset.xml");
        while (resources.hasMoreElements()) {
            urls.add(resources.nextElement());
        }
        return urls;
    }

    /**
     * Returns a collection of URLs pointing to navigation descriptors.
     *
     * @return a non-null collection;
     */
    static Collection<URL> getNavigationDescriptors() throws IOException {
        Collection<URL> urls = new ArrayList<>();
        Enumeration<URL> resources = ApplicationUtils.class.getClassLoader().getResources("navigation.xml");
        while (resources.hasMoreElements()) {
            urls.add(resources.nextElement());
        }
        return urls;
    }


}
