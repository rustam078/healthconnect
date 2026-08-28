package in.healthconnect.widgetengine.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Tests for the row-shaping part of WidgetQueryExecutor.
// The actual database call is one line and is checked later by an integration test.
// Here we test the pure logic that we can run WITHOUT a database:
//   - cut the one extra row and report hasNext
//   - optionally hide technical columns (id, *_id, dates)
//   - keep the columns in their original order
class WidgetQueryExecutorTest {

    // small helper to build a row in a fixed order
    private Map<String, Object> row(Object... keyValues) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    void hasNextIsTrueWhenAnExtraRowCameBack() {
        // page size is 2, but 3 rows came back -> there IS a next page, keep only 2
        List<Map<String, Object>> raw = new ArrayList<>(List.of(
                row("Patient", "A"), row("Patient", "B"), row("Patient", "C")));

        ExecutionResult result = WidgetQueryExecutor.shape(raw, 2, false);

        assertTrue(result.hasNext());
        assertEquals(2, result.rows().size());
        assertEquals("A", result.rows().get(0).get("Patient"));
        assertEquals("B", result.rows().get(1).get("Patient"));
    }

    @Test
    void hasNextIsFalseWhenExactlyPageSizeCameBack() {
        List<Map<String, Object>> raw = new ArrayList<>(List.of(
                row("Patient", "A"), row("Patient", "B")));

        ExecutionResult result = WidgetQueryExecutor.shape(raw, 2, false);

        assertFalse(result.hasNext());
        assertEquals(2, result.rows().size());
    }

    @Test
    void hasNextIsFalseWhenFewerRowsCameBack() {
        List<Map<String, Object>> raw = new ArrayList<>(List.of(row("Patient", "A")));

        ExecutionResult result = WidgetQueryExecutor.shape(raw, 2, false);

        assertFalse(result.hasNext());
        assertEquals(1, result.rows().size());
    }

    @Test
    void technicalColumnsAreHiddenWhenAsked() {
        List<Map<String, Object>> raw = new ArrayList<>(List.of(
                row("Patient", "A", "id", 1, "doctor_id", 9,
                        "from_date", "2026-01-01", "to_date", "2026-02-01", "Status", "ACTIVE")));

        ExecutionResult result = WidgetQueryExecutor.shape(raw, 10, true);

        // only the display columns remain
        assertEquals(List.of("Patient", "Status"),
                new ArrayList<>(result.rows().get(0).keySet()));
    }

    @Test
    void allColumnsAreKeptWhenHideIsOff() {
        List<Map<String, Object>> raw = new ArrayList<>(List.of(row("Patient", "A", "id", 1)));

        ExecutionResult result = WidgetQueryExecutor.shape(raw, 10, false);

        assertTrue(result.rows().get(0).containsKey("id"));
    }

    @Test
    void columnOrderIsKept() {
        List<Map<String, Object>> raw = new ArrayList<>(List.of(
                row("Name", "A", "Age", "30", "City", "X")));

        ExecutionResult result = WidgetQueryExecutor.shape(raw, 10, true);

        assertEquals(List.of("Name", "Age", "City"),
                new ArrayList<>(result.rows().get(0).keySet()));
    }
}
