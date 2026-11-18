package in.healthconnect.community.dto;

import java.util.List;

// A page of history, oldest first, plus whether older messages still exist (cursor paging).
public record ChatHistoryDto(List<ChatMessageDto> messages, boolean hasMore) {
}
