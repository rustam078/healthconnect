package in.healthconnect.community.realtime;

import java.util.Collection;

// Something open chat screens must hear about. Published inside a transaction and pushed to the
// SSE streams only after it commits (see ChatEventListener).
public record ChatEvent(Collection<Integer> receiverIds, String name, Object data) {

    public static final String MESSAGE = "message";
    public static final String MESSAGE_DELETED = "message_deleted";
    public static final String COMMUNITIES_CHANGED = "communities_changed";
}
