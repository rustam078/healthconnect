package in.healthconnect.community.realtime;

import in.healthconnect.community.repository.CommunityMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

// Tells the people who share a group with a user when that user comes online or goes offline,
// and hands a freshly connected user the list of co-members already online.
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);
    private static final String PRESENCE = "presence";
    private static final String PRESENCE_SNAPSHOT = "presence_snapshot";

    private final CommunityMemberRepository memberRepository;
    private final ChatStreamRegistry registry;

    public PresenceService(CommunityMemberRepository memberRepository, ChatStreamRegistry registry) {
        this.memberRepository = memberRepository;
        this.registry = registry;
    }

    @EventListener
    @Transactional(readOnly = true)
    public void onPresenceChanged(PresenceChangedEvent event) {
        List<Integer> coMembers = memberRepository.findCoMemberUserIds(event.userId());
        log.debug("Presence: user {} online={} -> notifying {} co-members", event.userId(), event.online(), coMembers.size());
        registry.sendToUsers(coMembers, PRESENCE, Map.of("userId", event.userId(), "online", event.online()));
        if (event.online()) {
            List<Integer> onlineNow = coMembers.stream().filter(registry::isOnline).toList();
            registry.sendToUsers(List.of(event.userId()), PRESENCE_SNAPSHOT, Map.of("online", onlineNow));
        }
    }
}
