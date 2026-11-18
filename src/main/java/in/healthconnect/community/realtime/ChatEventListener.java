package in.healthconnect.community.realtime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Pushes events to the SSE streams only after the DB commit, so screens never show rolled-back data.
// For more than one app instance: publish to Redis here and let each instance push to its own streams.
@Component
public class ChatEventListener {

    private final ChatStreamRegistry registry;

    public ChatEventListener(ChatStreamRegistry registry) {
        this.registry = registry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChatEvent(ChatEvent event) {
        registry.sendToUsers(event.receiverIds(), event.name(), event.data());
    }
}
