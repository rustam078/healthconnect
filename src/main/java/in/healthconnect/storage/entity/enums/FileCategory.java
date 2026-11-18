package in.healthconnect.storage.entity.enums;

import java.util.Set;

// What a stored file is for. Each category carries its own upload policy (allowed types, size)
// and its own disk sub-folder, so one file API can safely serve very different uses.
public enum FileCategory {

    PROFILE_IMAGE(2, Set.of("image/png", "image/jpeg", "image/webp")),
    LAB_REPORT(10, Set.of("application/pdf")),
    COMMUNITY_CHAT(10, Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif",
            "application/pdf", "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

    private final long maxBytes;
    private final Set<String> allowedTypes;

    FileCategory(int maxMb, Set<String> allowedTypes) {
        this.maxBytes = (long) maxMb * 1024 * 1024;
        this.allowedTypes = allowedTypes;
    }

    // True when a file of this content type and size may be uploaded under this category.
    public boolean allows(String contentType, long size) {
        return contentType != null && allowedTypes.contains(contentType) && size > 0 && size <= maxBytes;
    }

    public String folder() {
        return name().toLowerCase().replace('_', '-');
    }
}
