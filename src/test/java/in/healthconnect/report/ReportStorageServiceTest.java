package in.healthconnect.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

// Tests for local file storage: every stored file gets a UNIQUE name (even for identical
// content / same display name), and we can read the exact bytes back.
class ReportStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesWithUniqueNamesAndLoadsBack() {
        ReportStorageService storage = new ReportStorageService(tempDir.toString());

        String first = storage.store("hello".getBytes(), "pdf");
        String second = storage.store("hello".getBytes(), "pdf");

        assertNotEquals(first, second); // unique even though the content is the same
        assertTrue(first.endsWith(".pdf"));
        assertArrayEquals("hello".getBytes(), storage.load(first));
    }
}
