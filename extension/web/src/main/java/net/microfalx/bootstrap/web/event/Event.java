package net.microfalx.bootstrap.web.event;

import net.microfalx.lang.Identifiable;
import net.microfalx.lang.Nameable;

/**
 * An event.
 * <p>
 * The name of the event is given by the {@link net.microfalx.lang.annotation.Name} annotation. In the absence of
 * such annotation, the simple class name is used as the event name by removing the suffix "Event".
 * <p>
 * The event parameters con be defined as members of the class event implementing this interface. The
 * parameters are passed to and from the client in the same order they are defined in the class.
 * <p>
 * Subclasses should extend {@link AbstractEvent} instead of implementing this interface directly and provide a no argument
 * constructor.
 * <p>
 * Each instance of the event is associated with the current application identifier.
 * <p>
 * <b>Warning:</b> the application identifier is captured when the event is created, from the application bound to
 * the current thread (see {@link net.microfalx.bootstrap.web.application.Application#current()}). The application is
 * bound only to threads which handle web container requests. If events are created outside the web container
 * requests (thread pools, scheduled tasks, asynchronous callbacks, etc.), the application must be assigned manually
 * with {@code setApplication(String)}, using an identifier captured while processing the request
 * (for example {@code Application.current()} called before the work is handed off to another thread). Otherwise, the
 * event is associated with no application ({@code na}) or with a stale application left on a reused thread, and the
 * event never reaches the intended page.
 */
public interface Event extends Identifiable<String>, Nameable {

    /**
     * Returns whether the event is an internal ping event.
     * <p>
     * Ping events are used to keep the connection alive and are not sent to the client.
     *
     * @return {@code true} if the event is a ping event, {@code false} otherwise
     */
    boolean isPing();

    /**
     * Returns the application identifier the event is associated with.
     *
     * @return a non-null instance
     */
    String getApplication();
}
