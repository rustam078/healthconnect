package in.healthconnect.report;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

// Saves report files on local disk under a unique name (UUID), so two uploads never collide
// even if they share a display name. Metadata (which file, for whom) lives in the database.
@Service
public class ReportStorageService {

    private final Path baseDir;

    public ReportStorageService(@Value("${app.reports.dir:uploads/reports}") String directory) {
        this.baseDir = Paths.get(directory);
    }

    public String store(byte[] content, String extension) {
        ensureDir();
        String storedName = UUID.randomUUID() + "." + extension;
        try {
            Files.write(baseDir.resolve(storedName), content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save report file", e);
        }
        return storedName;
    }

    public byte[] load(String storedFileName) {
        try {
            return Files.readAllBytes(baseDir.resolve(storedFileName));
        } catch (IOException e) {
            throw new UncheckedIOException("Report file not found: " + storedFileName, e);
        }
    }

    // Permanently remove a stored file (ignored if it is already gone).
    public void delete(String storedFileName) {
        try {
            Files.deleteIfExists(baseDir.resolve(storedFileName));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete report file: " + storedFileName, e);
        }
    }

    private void ensureDir() {
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create reports directory", e);
        }
    }
}
