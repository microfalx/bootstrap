package net.microfalx.bootstrap.web.event;

/**
 * A destination for events sent to the client side (usually an SSE connection).
 * <p>
 * A sink is registered with {@link EventService#register(String, EventSink)} for an application. The service
 * guarantees that a sink is never called concurrently: events for an application are delivered in order, by at most one
 * thread at a time.
 */
public interface EventSink {

    /**
     * Sends an event to the client.
     *
     * @param event the event
     * @throws Exception if the event cannot be sent; the sink is unregistered and closed
     */
    void send(Event event) throws Exception;

    /**
     * Invoked when the sink is not used anymore (replaced by another sink, failed to send an event or the
     * application is destroyed).
     */
    default void close() {
        // empty by default
    }
}
