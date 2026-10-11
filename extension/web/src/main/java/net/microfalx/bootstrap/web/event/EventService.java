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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.unmodifiableCollection;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;
import static net.microfalx.lang.ClassUtils.isSubClassOf;
import static net.microfalx.lang.ExceptionUtils.rethrowExceptionAndReturn;
import static net.microfalx.lang.TimeUtils.FIVE_MINUTE;

/**
 * A service which manages events.
 */
@Service
@Slf4j
public class EventService implements InitializingBean {

    public static final long SSE_TIMEOUT = FIVE_MINUTE;
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
     * Polls the next event to be sent to the client side.
     *
     * @param <E> the event type
     * @return the event or null if none is available
     */
    @SuppressWarnings("unchecked")
    public <E> E poll(String application) {
        ApplicationQueue queue = getQueue(application);
        return (E) queue.poll();
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
            getQueue(application).add(event);
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
     * Pings all applications to keep the connections alive.
     */
    public void ping() {
        for (ApplicationQueue queue : events.values()) {
            publish(new PingEvent().setApplication(queue.getId()));
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

    private void initThreadPool() {
        if (threadPool == null) {
            threadPool = ThreadPool.builder("SSE").maximumSize(8).queueSize(100).build();
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
        events.remove(id);
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
        }
    }

    private static class ApplicationQueue implements Identifiable<String> {

        private final String id;
        private final BlockingQueue<Event> events = new LinkedBlockingQueue<>();
        private final long created = currentTimeMillis();
        private volatile long lastUsed = created;

        ApplicationQueue(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        void add(Event event) {
            if (!isSubClassOf(event, PingEvent.class)) touch();
            if (!events.offer(event)) {
                LOGGER.warn("Event queue for application '{}' is full, dropping event: {}", id, event.getName());
            }
        }

        Event poll() {
            try {
                return events.poll(500, MILLISECONDS);
            } catch (InterruptedException e) {
                return rethrowExceptionAndReturn(e);
            }
        }

        void touch() {
            lastUsed = currentTimeMillis();
        }

        boolean isStale() {
            return (currentTimeMillis() - lastUsed) > APPLICATION_TIMEOUT;
        }
    }
}
