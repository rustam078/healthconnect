package in.healthconnect.community.realtime;

// Fired when a user opens their first stream (online) or closes their last one (offline).
public record PresenceChangedEvent(Integer userId, boolean online) {
}
