package in.healthconnect.storage.service;

import in.healthconnect.storage.entity.enums.FileCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

// Reads and writes file bytes on local disk, one sub-folder per category, unique UUID names
// so two uploads never collide. The DB only ever stores the returned key, not the bytes.
@Service
public class FileStorageService {

    private final Path baseDir;

    public FileStorageService(@Value("${app.files.dir:uploads}") String directory) {
        this.baseDir = Paths.get(directory);
    }

    // Saves the bytes and returns the storage key (relative "<category>/<uuid>.<ext>").
    public String store(byte[] content, String extension, FileCategory category) {
        String name = UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension);
        String key = category.folder() + "/" + name;
        Path target = baseDir.resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save file", e);
        }
        return key;
    }

    public byte[] load(String storageKey) {
        try {
            return Files.readAllBytes(baseDir.resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read file: " + storageKey, e);
        }
    }

    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(baseDir.resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete file: " + storageKey, e);
        }
    }
}
