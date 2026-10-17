package net.microfalx.bootstrap.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.bootstrap.core.utils.Failure;
import net.microfalx.bootstrap.web.application.Application;
import net.microfalx.bootstrap.web.application.ApplicationUtils;
import net.microfalx.bootstrap.web.event.*;
import net.microfalx.lang.ExceptionUtils;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicBoolean;

import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;
import static net.microfalx.lang.StringUtils.NA_ID_STRING;
import static net.microfalx.lang.StringUtils.isEmpty;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE;

@RestController("sse")
@RequestMapping("/event")
@Slf4j
public class EventController implements AnonymousController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private EventService eventService;

    @ModelAttribute
    public void setHeaders(HttpServletRequest request) {
        String appId = request.getHeader("X-Application-Id");
        if (isEmpty(appId)) {
            appId = request.getParameter("applicationId");
        }
        if (isEmpty(appId)) {
            throw new EventException("Application identifier (X-Application-Id header or request parameter) is not set");
        }
        Application.set(appId);
        MDC.put("AppId", ApplicationUtils.getShortId(appId));
    }

    @GetMapping(path = "/out", produces = TEXT_EVENT_STREAM_VALUE)
    public SseEmitter outEvent() {
        String application = Application.current();
        if (isEmpty(application)) {
            throw new EventException("Application identifier is not available for SSE connection");
        }
        SseEmitter emitter = new SseEmitter(0L);
        EmitterSink sink = new EmitterSink(emitter, application);
        emitter.onCompletion(sink::onCompletion);
        emitter.onTimeout(sink::onCompletion);
        emitter.onError(sink::onError);
        eventService.register(application, sink);
        LOGGER.debug("Start SSE emitter for application {}", application);
        return emitter;
    }

    @PostMapping(value = "/in/{name}", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public void inEvent(@PathVariable("name") String name, @RequestBody String payload) {
        Event event = eventService.decodeEvent(payload);
        eventService.publish(event);
    }

    /**
     * An event sink which sends the events over an SSE connection.
     */
    private class EmitterSink implements EventSink {

        private final SseEmitter emitter;
        private final String application;
        private final AtomicBoolean closed = new AtomicBoolean();

        private EmitterSink(SseEmitter emitter, String application) {
            this.emitter = emitter;
            this.application = application;
        }

        @Override
        public void send(Event event) throws Exception {
            Level level = event instanceof PingEvent ? Level.TRACE : Level.DEBUG;
            LOGGER.atLevel(level).log("Send event {} for application {}", event, application);
            if (!event.isPing()) {
                System.out.println("### Send event " + event + " for application " + application);
            }
            SseEmitter.SseEventBuilder builder = SseEmitter.event().id(event.getId());
            if (event instanceof PingEvent) {
                builder.comment("ping");
            } else {
                builder.comment("application");
            }
            try {
                builder.data(OBJECT_MAPPER.writeValueAsString(event));
                emitter.send(builder);
            } catch (Exception e) {
                if (isClientAbortError(e) || !isFatalError(e)) {
                    LOGGER.debug("Communication error with client reason: {}", getRootCauseDescription(e));
                } else {
                    LOGGER.warn("Error while sending event {}, root cause: {}", event, getRootCauseDescription(e));
                }
                throw e;
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    LOGGER.debug("Failed to complete SSE emitter for application {}, root cause: {}", application,
                            getRootCauseDescription(e));
                }
            }
        }

        private void onCompletion() {
            closed.set(true);
            eventService.unregister(application, this);
        }

        private void onError(Throwable throwable) {
            closed.set(true);
            eventService.unregister(application, this);
            if (isClientAbortError(throwable) || !isFatalError(throwable)) {
                LOGGER.debug("SSE connection closed for application {}, reason: {}", application,
                        getRootCauseDescription(throwable));
            } else {
                LOGGER.warn("SSE connection failed for application {}, root cause: {}", application,
                        getRootCauseDescription(throwable));
            }
        }

        private boolean isFatalError(Throwable e) {
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            // TODO still not clear what it means? However, do not log as error since it does not seem to affect functionality
            return !(rootCause instanceof AsyncRequestNotUsableException);
        }

        private boolean isClientAbortError(Throwable throwable) {
            return throwable instanceof IllegalStateException || Failure.getType(throwable) == Failure.Type.RESET;
        }
    }
}
