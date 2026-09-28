package net.microfalx.bootstrap.application;

import lombok.Getter;
import lombok.ToString;
import net.microfalx.lang.Descriptable;
import net.microfalx.lang.Identifiable;
import net.microfalx.lang.Nameable;

import java.util.Objects;
import java.util.TimeZone;

/**
 * A class which holds information about current application.
 */
@Getter
@ToString
public final class Application implements Identifiable<String>, Nameable, Descriptable {

    String id;
    String name;
    String description;
    String vendor;
    String url;
    String version;
    String buildNumber;
    String buildTime;
    String executable;
    TimeZone timeZone;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Application that)) return false;
        return Objects.equals(name, that.name) && Objects.equals(vendor, that.vendor) && Objects.equals(url, that.url)
                && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, vendor, url, version);
    }

}
