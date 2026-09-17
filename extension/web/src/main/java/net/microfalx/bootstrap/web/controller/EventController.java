package net.microfalx.bootstrap.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.bootstrap.core.utils.Failure;
import net.microfalx.bootstrap.web.event.*;
import net.microfalx.lang.ExceptionUtils;
import net.microfalx.threadpool.ThreadPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.System.currentTimeMillis;
import static net.microfalx.bootstrap.web.event.EventService.SSE_TIMEOUT;
import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;
import static net.microfalx.lang.ExceptionUtils.rethrowException;
import static net.microfalx.lang.StringUtils.isEmpty;
import static net.microfalx.lang.ThreadUtils.sleepMillis;
import static net.microfalx.lang.TimeUtils.millisSince;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE;

@RestController("sse")
@RequestMapping("/event")
@Slf4j
public class EventController implements AnonymousController {

    @Autowired private EventService eventService;

    private String application;

    @ModelAttribute
    public void setHeaders(HttpServletRequest request) {
        application = request.getHeader("X-Application-Id");
        if (isEmpty(application)) {
            application = request.getParameter("applicationId");
        }
        if (isEmpty(application)) {
            throw new EventException("X-Application-Id header is not set");
        }
    }

    @GetMapping(path = "/out", produces = TEXT_EVENT_STREAM_VALUE)
    public SseEmitter outEvent() {
        SseEmitter emitter = new SseEmitter(0L);
        ThreadPool threadPool = eventService.getThreadPool();
        threadPool.execute(new EventsTask(emitter, application, eventService));
        return emitter;
    }

    @PostMapping(value = "/in/{name}", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public void inEvent(@PathVariable("name") String name, @RequestBody String payload) {
        Event event = eventService.decodeEvent(payload);
        eventService.publish(event);
    }

    private static class EventsTask implements Runnable {

        private final SseEmitter emitter;
        private final String application;
        private final EventService eventService;
        private final AtomicInteger index = new AtomicInteger(1);
        private final ObjectMapper objectMapper;
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private volatile Throwable throwable;

        public EventsTask(SseEmitter emitter, String application, EventService eventService) {
            this.emitter = emitter;
            this.application = application;
            this.emitter.onCompletion(this::onCompletion);
            this.emitter.onError(this::onError);
            this.eventService = eventService;
            this.objectMapper = new ObjectMapper();
        }

        private void sendEvent(Event event) {
            SseEmitter.SseEventBuilder builder = SseEmitter.event().id(event.getId());
            if (event instanceof PingEvent) {
                builder.comment("ping");
            } else {
                builder.comment("application");
            }
            try {
                String data = objectMapper.writeValueAsString(event);
                builder.data(data);
                emitter.send(builder);
            } catch (IllegalStateException e) {
                rethrowException(e);
            } catch (Exception e) {
                throw new EventException("Failed to send event '" + event.getName() + "'", e);
            }
        }

        private void onCompletion() {
            completed.set(true);
        }

        private void onError(Throwable throwable) {
            completed.set(true);
            this.throwable = throwable;
        }

        private boolean isFatalError(Throwable e) {
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            // TODO still not clear what it means? However, do not log as error since it does not seem to affect functionality
            return !(rootCause instanceof AsyncRequestNotUsableException);
        }

        private boolean isClientAbortError(Throwable throwable) {
            return throwable instanceof IllegalStateException || Failure.getType(throwable) == Failure.Type.RESET;
        }

        @Override
        public void run() {
            try {
                long lastEvent = currentTimeMillis();
                while (!completed.get() && millisSince(lastEvent) < SSE_TIMEOUT) {
                    Event event = eventService.poll(application);
                    if (event != null) {
                        sendEvent(event);
                        lastEvent = currentTimeMillis();
                    } else {
                        sleepMillis(20);
                    }
                }
                sendEvent(new CloseEvent().setApplication(application));
            } catch (IllegalStateException e) {
                throwable = e;
            } catch (Exception e) {
                if (isFatalError(e)) throwable = e;
                emitter.completeWithError(e);
            } finally {
                emitter.complete();
            }
            if (throwable != null) {
                if (isClientAbortError(throwable)) {
                    LOGGER.debug("Communication error with client reason: {}", getRootCauseDescription(throwable));
                } else {
                    LOGGER.warn("Error while processing events, root cause: {}", getRootCauseDescription(throwable));
                }
            }
        }
    }
}
