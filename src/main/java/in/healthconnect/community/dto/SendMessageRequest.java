package in.healthconnect.community.dto;

import jakarta.validation.constraints.Size;

// A message carries text, an attachment (fileId), or both. The service rejects an empty one.
public record SendMessageRequest(@Size(max = 4000) String message, Integer fileId) {
}
