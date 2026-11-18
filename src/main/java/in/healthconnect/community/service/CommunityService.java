package in.healthconnect.community.service;

import in.healthconnect.community.dto.CommunityDto;
import in.healthconnect.community.dto.CreateCommunityRequest;
import in.healthconnect.community.dto.MemberDto;
import in.healthconnect.community.entity.ChatMessage;
import in.healthconnect.community.entity.Community;
import in.healthconnect.community.entity.CommunityMember;
import in.healthconnect.community.entity.enums.CommunityVisibility;
import in.healthconnect.community.entity.enums.MemberRole;
import in.healthconnect.community.entity.enums.MessageType;
import in.healthconnect.community.entity.enums.UserType;
import in.healthconnect.community.exception.CommunityException;
import in.healthconnect.community.realtime.ChatEvent;
import in.healthconnect.community.repository.ChatMessageRepository;
import in.healthconnect.community.repository.CommunityMemberRepository;
import in.healthconnect.community.repository.CommunityRepository;
import in.healthconnect.community.dto.ChatMessageDto;
import in.healthconnect.security.entity.User;
import in.healthconnect.security.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Groups, membership and the access rules: who may discover, create, join and manage a community.
@Service
public class CommunityService {

    private static final Logger log = LoggerFactory.getLogger(CommunityService.class);

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final UserTypeResolver userType;
    private final DisplayNameResolver displayName;
    private final ApplicationEventPublisher eventPublisher;

    public CommunityService(CommunityRepository communityRepository, CommunityMemberRepository memberRepository,
                            ChatMessageRepository messageRepository, UserRepository userRepository,
                            UserTypeResolver userType, DisplayNameResolver displayName,
                            ApplicationEventPublisher eventPublisher) {
        this.communityRepository = communityRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.userType = userType;
        this.displayName = displayName;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<CommunityDto> findMyCommunities(Integer userId) {
        return communityRepository.findCommunitiesOfUser(userId);
    }

    // Groups the caller may still join, limited to what their account type is allowed to see.
    @Transactional(readOnly = true)
    public List<CommunityDto> discover(User me) {
        List<CommunityVisibility> visibilities = userType.of(me) == UserType.PATIENT
                ? List.of(CommunityVisibility.PUBLIC)
                : List.of(CommunityVisibility.PUBLIC, CommunityVisibility.PUBLIC_DOCTORS);
        return communityRepository.findDiscoverable(me.getId(), visibilities);
    }

    // Creates a group; the caller becomes its admin and the chosen non-patient users its members.
    @Transactional
    public CommunityDto create(User me, CreateCommunityRequest request) {
        Community community = communityRepository.save(new Community(
                request.name().trim(), request.description(), request.visibility(), me.getId()));
        memberRepository.save(new CommunityMember(community.getId(), me.getId(), MemberRole.ADMIN));
        addMemberRows(community.getId(), request.memberIds());
        notifyChanged(memberRepository.findUserIds(community.getId()), community.getId());
        log.info("Community {} created by user {} ({})", community.getId(), me.getId(), community.getVisibility());
        return myCommunity(community.getId(), me.getId());
    }

    // Soft-deletes a group (group admin only); every member's list refreshes.
    @Transactional
    public void delete(User me, Integer communityId) {
        requireAdmin(communityId, me.getId());
        Community community = requireCommunity(communityId);
        List<Integer> toNotify = memberRepository.findUserIds(communityId);
        community.setDeleted(true);
        log.info("Community {} deleted by user {}", communityId, me.getId());
        notifyChanged(toNotify, communityId);
    }

    @Transactional(readOnly = true)
    public List<MemberDto> findMembers(Integer communityId, Integer userId) {
        requireMember(communityId, userId);
        return memberRepository.findMembers(communityId);
    }

    // Admin adds non-patient users; each addition shows as an inline system message.
    @Transactional
    public List<MemberDto> addMembers(User me, Integer communityId, List<Integer> newMemberIds) {
        requireAdmin(communityId, me.getId());
        Set<Integer> before = new HashSet<>(memberRepository.findUserIds(communityId));
        addMemberRows(communityId, newMemberIds);
        List<Integer> after = memberRepository.findUserIds(communityId);
        after.stream().filter(id -> !before.contains(id))
                .forEach(id -> systemMessage(communityId, me.getId(), nameOf(id) + " was added"));
        notifyChanged(after, communityId);
        return memberRepository.findMembers(communityId);
    }

    // Self-join a public group (type permitting); shows "X joined" inline.
    @Transactional
    public CommunityDto join(User me, Integer communityId) {
        Community community = requireCommunity(communityId);
        if (!canJoin(community.getVisibility(), userType.of(me))) {
            throw new CommunityException(HttpStatus.FORBIDDEN, "you cannot join this group");
        }
        if (!memberRepository.existsByCommunityIdAndUserId(communityId, me.getId())) {
            memberRepository.save(new CommunityMember(communityId, me.getId(), MemberRole.MEMBER));
            systemMessage(communityId, me.getId(), me.getUsername() + " joined");
            notifyChanged(memberRepository.findUserIds(communityId), communityId);
        }
        return myCommunity(communityId, me.getId());
    }

    // Admin removes another member, or a member removes themselves (leave). The admin cannot leave.
    @Transactional
    public void removeMember(User me, Integer communityId, Integer memberId) {
        CommunityMember actor = requireMember(communityId, me.getId());
        boolean leaving = memberId.equals(me.getId());
        if (!leaving && actor.getRole() != MemberRole.ADMIN) {
            throw new CommunityException(HttpStatus.FORBIDDEN, "only an admin can remove other members");
        }
        if (leaving && actor.getRole() == MemberRole.ADMIN) {
            throw new CommunityException(HttpStatus.BAD_REQUEST, "the admin cannot leave the group");
        }
        CommunityMember target = memberRepository.findByCommunityIdAndUserId(communityId, memberId)
                .orElseThrow(() -> new CommunityException(HttpStatus.NOT_FOUND, "user is not a member"));
        List<Integer> toNotify = memberRepository.findUserIds(communityId);
        memberRepository.delete(target);
        systemMessage(communityId, me.getId(), nameOf(memberId) + (leaving ? " left" : " was removed"));
        notifyChanged(toNotify, communityId);
    }

    /** Membership check used by every chat API; 403 when the caller is not in the community. */
    @Transactional(readOnly = true)
    public CommunityMember requireMember(Integer communityId, Integer userId) {
        return memberRepository.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new CommunityException(HttpStatus.FORBIDDEN, "you are not a member of this group"));
    }

    @Transactional
    public void markRead(Integer communityId, Integer userId, Integer messageId) {
        requireMember(communityId, userId);
        memberRepository.markRead(communityId, userId, messageId);
    }

    @Transactional(readOnly = true)
    public List<Integer> memberIds(Integer communityId) {
        return memberRepository.findUserIds(communityId);
    }

    private boolean canJoin(CommunityVisibility visibility, UserType type) {
        return switch (visibility) {
            case PUBLIC -> true;
            case PUBLIC_DOCTORS -> type == UserType.DOCTOR || type == UserType.ADMIN;
            case PRIVATE -> false;
        };
    }

    private void requireAdmin(Integer communityId, Integer userId) {
        if (requireMember(communityId, userId).getRole() != MemberRole.ADMIN) {
            throw new CommunityException(HttpStatus.FORBIDDEN, "only the group admin can do this");
        }
    }

    private Community requireCommunity(Integer communityId) {
        return communityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityException(HttpStatus.NOT_FOUND, "group not found"));
    }

    // Inserts the users that exist, are enabled and are not patients, and are not members yet.
    private void addMemberRows(Integer communityId, Collection<Integer> userIds) {
        if (userIds == null) {
            return;
        }
        for (Integer id : new HashSet<>(userIds)) {
            if (id == null || memberRepository.existsByCommunityIdAndUserId(communityId, id)) {
                continue;
            }
            userRepository.findById(id)
                    .filter(u -> u.isEnabled() && userType.of(u) != UserType.PATIENT)
                    .ifPresent(u -> memberRepository.save(new CommunityMember(communityId, id, MemberRole.MEMBER)));
        }
    }

    // Saves a SYSTEM line (join/leave/added/removed) and pushes it so it appears inline for members.
    private void systemMessage(Integer communityId, Integer actorId, String text) {
        ChatMessage message = new ChatMessage(communityId, actorId, MessageType.SYSTEM);
        message.setBody(text);
        ChatMessage saved = messageRepository.save(message);
        ChatMessageDto dto = ChatMessageDto.from(saved, nameOf(actorId));
        eventPublisher.publishEvent(new ChatEvent(memberRepository.findUserIds(communityId), ChatEvent.MESSAGE, dto));
    }

    private void notifyChanged(Collection<Integer> receiverIds, Integer communityId) {
        eventPublisher.publishEvent(new ChatEvent(receiverIds, ChatEvent.COMMUNITIES_CHANGED,
                Map.of("communityId", communityId)));
    }

    private String nameOf(Integer userId) {
        return userRepository.findById(userId).map(displayName::of).orElse("User " + userId);
    }

    private CommunityDto myCommunity(Integer communityId, Integer userId) {
        return communityRepository.findCommunitiesOfUser(userId).stream()
                .filter(c -> c.id().equals(communityId)).findFirst()
                .orElseThrow(() -> new CommunityException(HttpStatus.NOT_FOUND, "group not found"));
    }
}
