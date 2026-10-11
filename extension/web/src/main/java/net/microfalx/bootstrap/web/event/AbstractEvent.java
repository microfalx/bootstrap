package net.microfalx.bootstrap.web.event;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import net.microfalx.bootstrap.web.application.Application;
import net.microfalx.lang.IdGenerator;

import java.util.StringJoiner;

/**
 * Base class for events.
 */
@Getter
@Setter
@EqualsAndHashCode
public abstract class AbstractEvent implements Event {

    private static final IdGenerator ID_GENERATOR = IdGenerator.get("web.event");

    private final String id = ID_GENERATOR.nextAsString();
    private final String name;
    private String application = Application.current();

    public AbstractEvent() {
        this.name = EventUtilities.getEventName(getClass());
    }

    protected void updateToString(StringJoiner joiner) {
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
                .add("id='" + id + "'")
                .add("name='" + name + "'")
                .add("application='" + application + "'");
        updateToString(joiner);
        return joiner.toString();
    }
}
