package in.healthconnect.widgetengine.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Tests for SqlCleaner.
// AI models often wrap the SQL in ```sql ... ``` fences or add a trailing ";".
// The cleaner removes those so we are left with a plain query the engine can use.
class SqlCleanerTest {

    private final SqlCleaner cleaner = new SqlCleaner();

    @Test
    void leavesAPlainQueryUnchanged() {
        assertEquals("SELECT 1", cleaner.clean("SELECT 1"));
    }

    @Test
    void trimsWhitespace() {
        assertEquals("SELECT 1", cleaner.clean("   SELECT 1   "));
    }

    @Test
    void removesSqlCodeFences() {
        assertEquals("SELECT 1", cleaner.clean("```sql\nSELECT 1\n```"));
    }

    @Test
    void removesPlainCodeFences() {
        assertEquals("SELECT 1", cleaner.clean("```\nSELECT 1\n```"));
    }

    @Test
    void removesTrailingSemicolon() {
        assertEquals("SELECT 1", cleaner.clean("SELECT 1;"));
    }

    @Test
    void removesFencesAndSemicolonTogether() {
        assertEquals("SELECT count(*) FROM doctors",
                cleaner.clean("```sql\nSELECT count(*) FROM doctors;\n```"));
    }
}
