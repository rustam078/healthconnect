package in.healthconnect.storage.service;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.storage.dto.StoredFileResponse;
import in.healthconnect.storage.entity.StoredFile;
import in.healthconnect.storage.entity.enums.FileCategory;
import in.healthconnect.storage.repository.StoredFileRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Orchestrates uploads and reads: validates per category, stores bytes on disk, saves the
// reference row, and gates reads through the matching FileAccessGuard.
@Service
public class FileService {

    private final FileStorageService storage;
    private final StoredFileRepository repository;
    private final Map<FileCategory, FileAccessGuard> guards;

    public FileService(FileStorageService storage, StoredFileRepository repository, List<FileAccessGuard> guardList) {
        this.storage = storage;
        this.repository = repository;
        this.guards = guardList.stream().collect(Collectors.toMap(FileAccessGuard::category, g -> g));
    }

    // Validates, stores the bytes on disk and saves the reference row.
    @Transactional
    public StoredFileResponse upload(MultipartFile file, FileCategory category, Integer referenceId, Integer userId) {
        validate(file, category);
        StoredFile entity = toEntity(file, category, referenceId, userId);
        entity.setStorageKey(storage.store(bytesOf(file), extensionOf(file.getOriginalFilename()), category));
        return StoredFileResponse.from(repository.save(entity));
    }

    // Loads a file only if the category's guard lets this user read it.
    @Transactional(readOnly = true)
    public StoredFile requireReadable(Integer fileId, Integer userId) {
        StoredFile file = repository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("file not found: " + fileId));
        FileAccessGuard guard = guards.get(file.getCategory());
        if (guard == null || !guard.canAccess(file, userId)) {
            throw new AccessDeniedException("you cannot access this file");
        }
        return file;
    }

    public byte[] bytes(StoredFile file) {
        return storage.load(file.getStorageKey());
    }

    // Internal lookup (no access guard) for other modules that validate the attachment themselves.
    @Transactional(readOnly = true)
    public StoredFile get(Integer fileId) {
        return repository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("file not found: " + fileId));
    }

    private void validate(MultipartFile file, FileCategory category) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        if (!category.allows(file.getContentType(), file.getSize())) {
            throw new IllegalArgumentException("file type or size not allowed for " + category);
        }
    }

    private StoredFile toEntity(MultipartFile file, FileCategory category, Integer referenceId, Integer userId) {
        StoredFile entity = new StoredFile();
        entity.setOriginalFilename(file.getOriginalFilename());
        entity.setContentType(file.getContentType());
        entity.setSizeBytes(file.getSize());
        entity.setCategory(category);
        entity.setReferenceId(referenceId);
        entity.setUploadedBy(userId);
        return entity;
    }

    private byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read upload", e);
        }
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
