package net.microfalx.bootstrap.web.event;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.bootstrap.web.application.ApplicationUtils;
import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.Identifiable;
import net.microfalx.lang.StringUtils;
import net.microfalx.threadpool.ThreadPool;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.unmodifiableCollection;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;
import static net.microfalx.lang.ClassUtils.isSubClassOf;
import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;
import static net.microfalx.lang.TimeUtils.FIVE_MINUTE;

/**
 * A service which manages events.
 * <p>
 * Events published for an application are queued and delivered to the {@link EventSink} registered for that
 * application (usually an SSE connection). Delivery is push based: publishing an event schedules a short-lived
 * task which drains the queue of the application, so no thread is held while a client is connected. Events published
 * while no sink is registered are kept until a sink is registered or the application becomes stale.
 */
@Service
@Slf4j
public class EventService implements InitializingBean {

    public static final long APPLICATION_TIMEOUT = FIVE_MINUTE;

    private final Map<String, ApplicationQueue> events = new ConcurrentHashMap<>();
    private final Map<Class<? extends Event>, EventListener<?>> listeners = new ConcurrentHashMap<>();
    private final Map<String, Class<? extends Event>> eventTypes = new ConcurrentHashMap<>();
    private EventSerde eventSerde;

    @Autowired
    private ApplicationContext applicationContext;

    private ThreadPool threadPool;

    /**
     * Returns the thread pool used to process events.
     *
     * @return a non-null instance
     */
    public ThreadPool getThreadPool() {
        return threadPool;
    }

    /**
     * Returns the registered event listeners.
     *
     * @return a non-null instance
     */
    public Collection<EventListener<?>> getEventListeners() {
        return unmodifiableCollection(listeners.values());
    }

    /**
     * Returns an event listener by name.
     *
     * @param name the name of the event
     * @return a non-null instance
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <E extends Event> EventListener<E> getEventListener(String name) {
        requireNonNull(name);
        Class<? extends Event> eventType = eventTypes.get(name.toLowerCase());
        if (eventType != null) {
            EventListener eventListener = listeners.get(eventType);
            if (eventListener != null) return eventListener;
        }
        throw new EventException("No event listener found for event '" + name + "'");
    }

    /**
     * Returns the event type by name.
     *
     * @param name the event name
     * @param <E>  the event type
     * @return a non-null instance
     */
    @SuppressWarnings("unchecked")
    public <E> Class<E> getEventType(String name) {
        requireNonNull(name);
        Class<E> eventType = (Class<E>) eventTypes.get(name.toLowerCase());
        if (eventType != null) return eventType;
        throw new EventException("No event type found for event '" + name + "'");
    }

    /**
     * Registers the sink which receives the events of an application.
     * <p>
     * A previously registered sink (if any) is replaced and closed. Events published before the registration are
     * delivered to the new sink.
     *
     * @param application the application identifier
     * @param sink        the sink
     */
    public void register(String application, EventSink sink) {
        requireNonNull(sink);
        ApplicationQueue queue = getQueue(application);
        EventSink previousSink = queue.setSink(sink);
        LOGGER.debug("Register event sink for application {}", application);
        if (previousSink != null && previousSink != sink) previousSink.close();
        scheduleDelivery(queue);
    }

    /**
     * Unregisters the sink of an application.
     * <p>
     * The sink is removed only if it is still the registered sink for the application.
     *
     * @param application the application identifier
     * @param sink        the sink
     */
    public void unregister(String application, EventSink sink) {
        requireNonNull(sink);
        ApplicationQueue queue = events.get(application);
        if (queue != null && queue.removeSink(sink)) {
            LOGGER.debug("Unregister event sink for application {}", application);
        }
    }

    /**
     * Publish an event from services to the client side.
     *
     * @param event the event
     */
    public void publish(Event event) {
        requireNonNull(event);
        String application = event.getApplication();
        if (StringUtils.isEmpty(application)) {
            throw new EventException("Event application is not set for event " + event);
        } else {
            Level level = event instanceof PingEvent ? Level.TRACE : Level.DEBUG;
            MDC.put("AppId", ApplicationUtils.getShortId(application));
            LOGGER.atLevel(level).log("Publish event {} for application {}", event, application);
            ApplicationQueue queue = getQueue(application);
            queue.add(event);
            scheduleDelivery(queue);
        }
    }

    /**
     * Decodes an event payload received from the client side.
     * <p>
     * The application associated with the event is the application currently in context.
     *
     * @param payload the payload
     * @param <E>     the event type
     * @return the event
     */
    @SuppressWarnings({"ReassignedVariable", "unchecked"})
    public <E> E decodeEvent(String payload) {
        JsonNode jsonNode = eventSerde.read(payload);
        String eventName = StringUtils.NA_STRING;
        Class<? extends Event> eventType = Event.class;
        try {
            eventName = EventUtilities.getEventName(jsonNode);
            eventType = getEventType(eventName);
            return (E) eventSerde.deserialize(jsonNode, eventType);
        } catch (Exception e) {
            throw new EventException("Event '" + eventName + "' (" + ClassUtils.getName(eventType) + ") cannot be decoded", e);
        }
    }

    /**
     * Publish an event received from the client side to server listeners.
     *
     * @param event the event
     */
    public <E extends Event> void receive(E event) {
        requireNonNull(event);
        EventListener<E> listener = getEventListener(event.getName());
        listener.onEvent(event);
    }

    /**
     * Pings all connected applications to keep the connections alive (and detect broken connections).
     */
    public void ping() {
        for (ApplicationQueue queue : events.values()) {
            if (queue.hasSink()) publish(new PingEvent().setApplication(queue.getId()));
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        loadListeners();
        initObjectMapper();
        initThreadPool();
        initWorkers();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void loadListeners() {
        Collection<EventListener> eventListeners = ClassUtils.resolveProviderInstances(EventListener.class);
        for (EventListener<?> listener : eventListeners) {
            Class<?> eventType = ClassUtils.getClassParametrizedType(listener.getClass(), 0);
            if (eventType == null || !Event.class.isAssignableFrom(eventType)) {
                LOGGER.error("Cannot resolve event type for event listener: {}", ClassUtils.getName(listener));
                continue;
            }
            String eventName = EventUtilities.getEventName(eventType);
            eventTypes.put(eventName, (Class<? extends Event>) eventType);
            EventListener existingListener = listeners.putIfAbsent((Class<? extends Event>) eventType, listener);
            if (existingListener != null) {
                LOGGER.error("Duplicate listener found for event '{}' - listeners: {}, {}", eventName, ClassUtils.getName(existingListener), ClassUtils.getName(listener));
            }
        }
        LOGGER.info("Loaded {} event listeners", listeners.size());
    }

    private ApplicationQueue getQueue(String application) {
        requireNotEmpty(application);
        return events.computeIfAbsent(application, ApplicationQueue::new);
    }

    private void scheduleDelivery(ApplicationQueue queue) {
        if (!queue.hasSink() || queue.isEmpty() || !queue.startDelivery()) return;
        try {
            threadPool.execute(new DeliveryTask(queue));
        } catch (RejectedExecutionException e) {
            queue.endDelivery();
            LOGGER.warn("Event delivery for application '{}' was rejected, events will be delivered later", queue.getId());
        }
    }

    private void initThreadPool() {
        if (threadPool == null) {
            threadPool = ThreadPool.builder("SSE")
                    .maximumSize(8)
                    .build();
        }
    }

    private void initWorkers() {
        threadPool.scheduleAtFixedRate(new MaintenanceTask(), ofSeconds(30));
    }

    private void initObjectMapper() {
        if (eventSerde == null) {
            eventSerde = new EventSerde();
        }
    }

    private void destroyApplication(String id) {
        ApplicationQueue queue = events.remove(id);
        if (queue != null) {
            EventSink sink = queue.getSink();
            if (sink != null && queue.removeSink(sink)) sink.close();
        }
    }

    private void destroyIdle() {
        for (ApplicationQueue queue : events.values()) {
            if (queue.isStale()) {
                destroyApplication(queue.getId());
            }
        }
    }

    private class MaintenanceTask implements Runnable {

        @Override
        public void run() {
            ping();
            destroyIdle();
            // retry applications with pending events where a previous delivery was rejected
            for (ApplicationQueue queue : events.values()) {
                scheduleDelivery(queue);
            }
        }
    }

    /**
     * Sends all pending events of an application to its sink.
     * <p>
     * Only one task runs at any time for an application, which keeps the events in order and guarantees that the sink
     * is not called concurrently.
     */
    private class DeliveryTask implements Runnable {

        private final ApplicationQueue queue;

        DeliveryTask(ApplicationQueue queue) {
            this.queue = queue;
        }

        @Override
        public void run() {
            MDC.put("AppId", ApplicationUtils.getShortId(queue.getId()));
            try {
                do {
                    deliver();
                    queue.endDelivery();
                    // an event (or a sink) might have arrived after the queue was drained, but before the
                    // delivery ended; in such case, continue if no other task took over
                } while (queue.hasSink() && !queue.isEmpty() && queue.startDelivery());
            } finally {
                MDC.remove("AppId");
            }
        }

        private void deliver() {
            EventSink sink;
            Event event;
            while ((sink = queue.getSink()) != null && (event = queue.poll()) != null) {
                try {
                    sink.send(event);
                } catch (Exception e) {
                    LOGGER.debug("Failed to send event {} for application {}, root cause: {}", event, queue.getId(),
                            getRootCauseDescription(e));
                    // keep the event for the next connection of the application, pings are useless
                    if (!(event instanceof PingEvent)) queue.addFirst(event);
                    if (queue.removeSink(sink)) sink.close();
                    return;
                }
            }
        }
    }

    private static class ApplicationQueue implements Identifiable<String> {

        private final String id;
        private final BlockingDeque<Event> events = new LinkedBlockingDeque<>();
        private final AtomicBoolean delivering = new AtomicBoolean();
        private final long created = currentTimeMillis();
        private volatile long lastUsed = created;
        private volatile EventSink sink;

        ApplicationQueue(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        EventSink getSink() {
            return sink;
        }

        boolean hasSink() {
            return sink != null;
        }

        synchronized EventSink setSink(EventSink sink) {
            EventSink previousSink = this.sink;
            this.sink = sink;
            touch();
            return previousSink;
        }

        synchronized boolean removeSink(EventSink sink) {
            if (this.sink != sink) return false;
            this.sink = null;
            touch();
            return true;
        }

        void add(Event event) {
            if (!isSubClassOf(event, PingEvent.class)) touch();
            if (!events.offer(event)) {
                LOGGER.warn("Event queue for application '{}' is full, dropping event: {}", id, event.getName());
            }
        }

        void addFirst(Event event) {
            events.offerFirst(event);
        }

        Event poll() {
            return events.poll();
        }

        boolean isEmpty() {
            return events.isEmpty();
        }

        boolean startDelivery() {
            return delivering.compareAndSet(false, true);
        }

        void endDelivery() {
            delivering.set(false);
        }

        void touch() {
            lastUsed = currentTimeMillis();
        }

        boolean isStale() {
            return sink == null && (currentTimeMillis() - lastUsed) > APPLICATION_TIMEOUT;
        }
    }
}
