package in.healthconnect.community.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

// Open SSE streams in memory, keyed by user id (one user can have several tabs open). One stream
// per user covers all their communities, so a message anywhere updates their unread badges live.
@Component
public class ChatStreamRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamRegistry.class);

    private final Map<Integer, Set<SseEmitter>> streamsByUser = new ConcurrentHashMap<>();
    private final long timeoutMs;
    private final ApplicationEventPublisher eventPublisher;

    public ChatStreamRegistry(@Value("${chat.sse.timeout-ms:1800000}") long timeoutMs,
                              ApplicationEventPublisher eventPublisher) {
        this.timeoutMs = timeoutMs;
        this.eventPublisher = eventPublisher;
    }

    // Opens a live stream for one chat screen; the browser reconnects by itself on timeout.
    public SseEmitter open(Integer userId) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        Set<SseEmitter> streams = streamsByUser.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>());
        boolean cameOnline = streams.isEmpty();
        streams.add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> remove(userId, emitter));
        send(userId, emitter, event("connected", Map.of("userId", userId)));
        log.info("Chat stream opened for user {} ({} open)", userId, streams.size());
        if (cameOnline) {
            eventPublisher.publishEvent(new PresenceChangedEvent(userId, true));
        }
        return emitter;
    }

    // Whether the user has at least one open stream right now (drives the online dot).
    public boolean isOnline(Integer userId) {
        Set<SseEmitter> streams = streamsByUser.get(userId);
        return streams != null && !streams.isEmpty();
    }

    public void sendToUsers(Collection<Integer> userIds, String name, Object data) {
        for (Integer userId : userIds) {
            streamsByUser.getOrDefault(userId, Set.of()).forEach(emitter -> send(userId, emitter, event(name, data)));
        }
    }

    // Keeps idle streams alive through proxies and drops the ones whose browser is gone.
    @Scheduled(fixedDelayString = "${chat.sse.heartbeat-ms:20000}")
    public void heartbeat() {
        streamsByUser.forEach((userId, emitters) ->
                emitters.forEach(emitter -> send(userId, emitter, SseEmitter.event().comment("ping"))));
    }

    private static SseEmitter.SseEventBuilder event(String name, Object data) {
        return SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON);
    }

    private void send(Integer userId, SseEmitter emitter, SseEmitter.SseEventBuilder built) {
        try {
            emitter.send(built);
        } catch (Exception e) {
            // Client is gone (closed tab / dropped network). Just drop our reference — the framework
            // completes the broken async request itself; calling complete() here would fire a failing
            // dispatch and log noisy errors. onError/onCompletion will also clean up.
            log.debug("Dropping dead chat stream for user {}: {}", userId, e.getMessage());
            remove(userId, emitter);
        }
    }

    private void remove(Integer userId, SseEmitter emitter) {
        boolean[] wentOffline = {false};
        streamsByUser.computeIfPresent(userId, (k, emitters) -> {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                wentOffline[0] = true;
                return null;
            }
            return emitters;
        });
        if (wentOffline[0]) {
            log.info("Chat stream closed; user {} is now offline", userId);
            eventPublisher.publishEvent(new PresenceChangedEvent(userId, false));
        }
    }
}
