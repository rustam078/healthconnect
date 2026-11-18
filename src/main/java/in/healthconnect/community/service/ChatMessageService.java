package in.healthconnect.community.service;

import in.healthconnect.community.dto.ChatHistoryDto;
import in.healthconnect.community.dto.ChatMessageDto;
import in.healthconnect.community.dto.SendMessageRequest;
import in.healthconnect.community.entity.ChatMessage;
import in.healthconnect.community.entity.enums.MessageType;
import in.healthconnect.community.exception.CommunityException;
import in.healthconnect.community.realtime.ChatEvent;
import in.healthconnect.community.repository.ChatMessageRepository;
import in.healthconnect.security.entity.User;
import in.healthconnect.storage.entity.StoredFile;
import in.healthconnect.storage.entity.enums.FileCategory;
import in.healthconnect.storage.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// Sending, reading and deleting messages. Membership is enforced via CommunityService.
@Service
public class ChatMessageService {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageService.class);

    private final ChatMessageRepository messageRepository;
    private final CommunityService communityService;
    private final FileService fileService;
    private final DisplayNameResolver displayName;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${chat.history.max-page-size:100}")
    private int maxPageSize;

    public ChatMessageService(ChatMessageRepository messageRepository, CommunityService communityService,
                              FileService fileService, DisplayNameResolver displayName,
                              ApplicationEventPublisher eventPublisher) {
        this.messageRepository = messageRepository;
        this.communityService = communityService;
        this.fileService = fileService;
        this.displayName = displayName;
        this.eventPublisher = eventPublisher;
    }

    // Saves a text and/or image message, then (after commit) pushes it to every member's screens.
    @Transactional
    public ChatMessageDto send(User me, Integer communityId, SendMessageRequest request) {
        communityService.requireMember(communityId, me.getId());
        String body = request.message() == null ? null : request.message().trim();
        if ((body == null || body.isBlank()) && request.fileId() == null) {
            throw new CommunityException(HttpStatus.BAD_REQUEST, "message or file is required");
        }
        StoredFile attachment = request.fileId() == null ? null
                : validateAttachment(request.fileId(), communityId, me.getId());
        ChatMessage saved = messageRepository.save(build(communityId, me.getId(), body, attachment));
        String attachmentName = attachment == null ? null : attachment.getOriginalFilename();
        ChatMessageDto dto = ChatMessageDto.from(saved, displayName.of(me), me.getAvatarFileId(), attachmentName);
        eventPublisher.publishEvent(new ChatEvent(communityService.memberIds(communityId), ChatEvent.MESSAGE, dto));
        log.debug("Message {} sent to community {} by user {}", saved.getId(), communityId, me.getId());
        return dto;
    }

    // Cursor-paged history, oldest first; loading the latest page marks the chat as read.
    @Transactional
    public ChatHistoryDto history(User me, Integer communityId, Integer beforeId, int limit) {
        communityService.requireMember(communityId, me.getId());
        int size = Math.min(Math.max(limit, 1), maxPageSize);
        Pageable page = PageRequest.of(0, size + 1); // one extra row tells whether older messages exist
        List<ChatMessageDto> rows = beforeId == null
                ? messageRepository.findLatest(communityId, page)
                : messageRepository.findBefore(communityId, beforeId, page);
        List<ChatMessageDto> messages = new ArrayList<>(rows.subList(0, Math.min(size, rows.size())));
        if (beforeId == null && !messages.isEmpty()) {
            communityService.markRead(communityId, me.getId(), messages.get(0).id());
        }
        Collections.reverse(messages);
        return new ChatHistoryDto(messages, rows.size() > size);
    }

    // Soft-deletes a message; only its sender may delete it.
    @Transactional
    public void delete(User me, Integer communityId, Integer messageId) {
        communityService.requireMember(communityId, me.getId());
        ChatMessage message = messageRepository.findByIdAndCommunityId(messageId, communityId)
                .orElseThrow(() -> new CommunityException(HttpStatus.NOT_FOUND, "message not found"));
        if (!message.getSenderId().equals(me.getId())) {
            throw new CommunityException(HttpStatus.FORBIDDEN, "only the sender can delete this message");
        }
        message.markDeleted();
        Map<String, Integer> payload = Map.of("id", messageId, "communityId", communityId);
        eventPublisher.publishEvent(new ChatEvent(communityService.memberIds(communityId),
                ChatEvent.MESSAGE_DELETED, payload));
    }

    // The attachment must be a chat file this user uploaded for this community.
    private StoredFile validateAttachment(Integer fileId, Integer communityId, Integer userId) {
        StoredFile file = fileService.get(fileId);
        boolean ok = file.getCategory() == FileCategory.COMMUNITY_CHAT
                && communityId.equals(file.getReferenceId()) && userId.equals(file.getUploadedBy());
        if (!ok) {
            throw new CommunityException(HttpStatus.BAD_REQUEST, "invalid attachment");
        }
        return file;
    }

    // An image attachment renders inline; any other file renders as a download card.
    private ChatMessage build(Integer communityId, Integer senderId, String body, StoredFile attachment) {
        MessageType type = attachment == null ? MessageType.TEXT
                : (isImage(attachment.getContentType()) ? MessageType.IMAGE : MessageType.FILE);
        ChatMessage message = new ChatMessage(communityId, senderId, type);
        message.setBody(body);
        message.setAttachmentId(attachment == null ? null : attachment.getId());
        return message;
    }

    private boolean isImage(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }
}
