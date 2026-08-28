package in.healthconnect.widgetengine.engine;

import in.healthconnect.widgetengine.entity.enums.FilterOperator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests for SqlSafetyGuard.
// These describe the rules FIRST, before we write the real code.
// The guard has one job: make sure a query is a safe read-only SELECT,
// and that a filter operator is one we allow.
class SqlSafetyGuardTest {

    private final SqlSafetyGuard guard = new SqlSafetyGuard();

    // ---- queries that SHOULD be allowed (they only read data) ----

    @Test
    void allowsPlainSelect() {
        assertTrue(guard.isSelectOnly("SELECT * FROM patients"));
    }

    @Test
    void allowsWithCte() {
        // A query can start with WITH (a helper block) and still just read data.
        assertTrue(guard.isSelectOnly("WITH x AS (SELECT 1) SELECT * FROM x"));
    }

    @Test
    void allowsLeadingSpacesAndNewLines() {
        assertTrue(guard.isSelectOnly("   \n  SELECT 1"));
    }

    @Test
    void allowsLowercaseSelect() {
        assertTrue(guard.isSelectOnly("select 1"));
    }

    @Test
    void allowsOneTrailingSemicolon() {
        assertTrue(guard.isSelectOnly("SELECT 1;"));
    }

    // ---- queries that SHOULD be blocked (they change or damage data) ----

    @Test
    void blocksUpdate() {
        assertFalse(guard.isSelectOnly("UPDATE patients SET name = 'x'"));
    }

    @Test
    void blocksDelete() {
        assertFalse(guard.isSelectOnly("DELETE FROM patients"));
    }

    @Test
    void blocksInsert() {
        assertFalse(guard.isSelectOnly("INSERT INTO patients(name) VALUES ('x')"));
    }

    @Test
    void blocksDrop() {
        assertFalse(guard.isSelectOnly("DROP TABLE patients"));
    }

    @Test
    void blocksNull() {
        assertFalse(guard.isSelectOnly(null));
    }

    @Test
    void blocksBlank() {
        assertFalse(guard.isSelectOnly("    "));
    }

    @Test
    void blocksTwoStatementsStuckTogether() {
        // A sneaky attempt: read something, then drop a table. Must be blocked.
        assertFalse(guard.isSelectOnly("SELECT 1; DROP TABLE patients"));
    }

    @Test
    void blocksSelectThatWritesToAFile() {
        // MySQL can write query results to a file - we never allow that.
        assertFalse(guard.isSelectOnly("SELECT * FROM patients INTO OUTFILE '/tmp/x'"));
    }

    // ---- the "assert" version throws a clear error instead of returning false ----

    @Test
    void assertSelectOnlyThrowsForNonSelect() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.assertSelectOnly("DELETE FROM patients"));
    }

    @Test
    void assertSelectOnlyPassesForSelect() {
        assertDoesNotThrow(() -> guard.assertSelectOnly("SELECT 1"));
    }

    // ---- operator checking ----

    @Test
    void requireOperatorReturnsKnownOperator() {
        assertEquals(FilterOperator.IN, guard.requireOperator("in"));
    }

    @Test
    void requireOperatorThrowsForUnknownOperator() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.requireOperator("bogus"));
    }
}
