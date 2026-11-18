package in.healthconnect.storage.dto;

import in.healthconnect.storage.entity.StoredFile;
import in.healthconnect.storage.entity.enums.FileCategory;

// What the API returns after an upload: the id and a URL the client uses to fetch the file back.
public record StoredFileResponse(Integer id, String url, String filename, String contentType,
                                 Long size, FileCategory category) {

    public static StoredFileResponse from(StoredFile file) {
        return new StoredFileResponse(file.getId(), "/api/v1/files/" + file.getId(),
                file.getOriginalFilename(), file.getContentType(), file.getSizeBytes(), file.getCategory());
    }
}
