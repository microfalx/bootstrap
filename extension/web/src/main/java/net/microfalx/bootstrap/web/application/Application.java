package net.microfalx.bootstrap.web.application;

import lombok.Getter;
import lombok.ToString;
import net.microfalx.lang.Descriptable;
import net.microfalx.lang.Nameable;

import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;

import static java.util.Optional.ofNullable;
import static net.microfalx.lang.StringUtils.defaultIfEmpty;

/**
 * A class which holds information about current web application.
 */
@Getter
@ToString
public final class Application implements Nameable, Descriptable {

    String name;
    String description;
    String owner;
    String url;
    String version;
    String buildNumber;
    String buildTime;
    String logo;

    Theme theme;
    Theme systemTheme;

    TimeZone timeZone;

    private final static ThreadLocal<String> APPLICATION = new ThreadLocal<>();

    /**
     * Returns the current application identifier.
     *
     * @return a non-null instance
     */
    public static String current() {
        return defaultIfEmpty(APPLICATION.get(), "na");
    }

    /**
     * Returns the application instance associated with the current thread.
     *
     * @return a non-null instance
     */
    public static Optional<String> get() {
        return ofNullable(APPLICATION.get());
    }

    /**
     * Changes the application instance associated with the current thread.
     *
     * @param id the id, null to remove
     */
    public static void set(String id) {
        if (id != null) {
            APPLICATION.set(id);
        } else {
            clear();
        }
    }

    /**
     * Removes the application instance associated with the current thread.
     */
    public static void clear() {
        APPLICATION.remove();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Application that)) return false;
        return Objects.equals(name, that.name) && Objects.equals(owner, that.owner) && Objects.equals(url, that.url)
                && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, owner, url, version);
    }


}
