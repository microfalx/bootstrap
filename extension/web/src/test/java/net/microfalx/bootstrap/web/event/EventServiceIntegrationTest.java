package net.microfalx.bootstrap.web.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(MockitoExtension.class)
class EventServiceIntegrationTest {

    private static final int ITERATION_COUNT = 50;
    private static final int APPLICATION_COUNT = 50;

    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private EventService eventService;

    private final AtomicInteger eventCount = new AtomicInteger();
    private final AtomicBoolean concurrentSend = new AtomicBoolean();
    private final Map<String, CountingSink> sinks = new ConcurrentHashMap<>();

    @BeforeEach
    void before() throws Exception {
        eventService.afterPropertiesSet();
    }

    @Test
    void multipleApplications() {
        for (int i = 1; i <= APPLICATION_COUNT; i++) {
            String application = Integer.toString(i);
            CountingSink sink = new CountingSink();
            sinks.put(application, sink);
            eventService.register(application, sink);
        }
        for (int i = 1; i <= ITERATION_COUNT; i++) {
            for (int j = 1; j <= APPLICATION_COUNT; j++) {
                eventService.publish(new TestEvent().setApplication(Integer.toString(j)));
            }
        }
        await().atMost(Duration.ofSeconds(10)).logging(s -> {
            System.out.println("Current event count: " + eventCount.get());
        }).until(() -> eventCount.get() == ITERATION_COUNT * APPLICATION_COUNT);
        sinks.values().forEach(sink -> assertEquals(ITERATION_COUNT, sink.count.get()));
        assertFalse(concurrentSend.get(), "A sink was called concurrently");
    }

    public static class TestEvent extends AbstractEvent {

    }

    private class CountingSink implements EventSink {

        private final AtomicInteger count = new AtomicInteger();
        private final AtomicBoolean sending = new AtomicBoolean();

        @Override
        public void send(Event event) {
            if (!sending.compareAndSet(false, true)) concurrentSend.set(true);
            try {
                count.incrementAndGet();
                eventCount.incrementAndGet();
            } finally {
                sending.set(false);
            }
        }
    }
}
