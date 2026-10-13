package net.microfalx.bootstrap.web.event;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.microfalx.bootstrap.web.application.Application;
import net.microfalx.lang.annotation.Name;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.threadpool.ThreadPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ThreadPool threadPool;

    @InjectMocks
    private EventService eventService;

    @BeforeEach
    void before() throws Exception {
        eventService.afterPropertiesSet();
    }

    @Test
    void pingFromClient() {
        eventService.receive(new PingEvent());
    }

    @Test
    void pingFromServer() {
        runTasksInline();
        List<Event> sent = new ArrayList<>();
        eventService.register(Application.current(), sent::add);
        assertTrue(sent.isEmpty());
        eventService.publish(new PingEvent());
        assertEquals(1, sent.size());
    }

    @Test
    void eventsPublishedBeforeRegisterAreDelivered() {
        runTasksInline();
        eventService.publish(new TestEvent());
        eventService.publish(new TestEvent());
        List<Event> sent = new ArrayList<>();
        eventService.register(Application.current(), sent::add);
        assertEquals(2, sent.size());
    }

    @Test
    void failedSinkIsUnregisteredAndEventIsKept() {
        runTasksInline();
        AtomicInteger closed = new AtomicInteger();
        eventService.register(Application.current(), new EventSink() {

            @Override
            public void send(Event event) throws Exception {
                throw new IOException("Broken pipe");
            }

            @Override
            public void close() {
                closed.incrementAndGet();
            }
        });
        eventService.publish(new TestEvent());
        assertEquals(1, closed.get());
        List<Event> sent = new ArrayList<>();
        eventService.register(Application.current(), sent::add);
        assertEquals(1, sent.size());
    }

    private void runTasksInline() {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(threadPool).execute(any(Runnable.class));
    }

    @Test
    void decodeEvent() {
        TestEvent event = eventService.decodeEvent("""
                {
                    id:"1",
                    name:"test",
                    arguments:["d1",10]
                }""");
        assertNotNull(event.getId());
        assertEquals("test", event.getName());
        assertEquals("na", event.getApplication());
        assertEquals("d1", event.getData());
        assertEquals(10, event.getIndex());
    }

    @Test
    void listeners() {
        assertEquals(2, eventService.getEventListeners().size());
    }

    @Getter
    @Setter
    @ToString(callSuper = true)
    @Name("test")
    public static class TestEvent extends AbstractEvent {

        private String data;
        private int index;
    }

    @Provider
    public static class TestEventListener implements EventListener<TestEvent> {

        @Override
        public void onEvent(TestEvent event) {
            // No-op
        }
    }

}