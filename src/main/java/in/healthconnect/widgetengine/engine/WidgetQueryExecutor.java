package in.healthconnect.widgetengine.engine;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// This class RUNS the prepared query on the database and tidies up the rows.
// Steps:
//   1. Ask the database for the rows (the query already asked for one extra row).
//   2. If we got the extra row, remove it and remember hasNext = true.
//   3. Optionally hide "technical" columns (like id) that are only used inside the query.
//   4. Keep the columns in the same order the query returned them.
@Component
public class WidgetQueryExecutor {

    // Spring's helper for running a query that uses :name placeholders.
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public WidgetQueryExecutor(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // How many rows the query matches in total, ignoring paging.
    //
    // The widget query is wrapped rather than rewritten: SELECT COUNT(*) FROM ( ... ) t.
    // Counting what the page actually came from is the only way to be right about a query
    // with its own GROUP BY, UNION or DISTINCT - trying to rewrite it into a count would be
    // guesswork about someone else's SQL.
    public long count(PreparedQuery prepared) {
        String sql = "SELECT COUNT(*) FROM (" + prepared.getCountableSql() + ") widget_rows";
        Long total = jdbcTemplate.queryForObject(sql, prepared.getParams(), Long.class);
        return total == null ? 0L : total;
    }

    // Run the query and return tidy rows + whether there is a next page.
    public ExecutionResult execute(PreparedQuery prepared, boolean hideTechnicalColumns) {
        List<Map<String, Object>> raw = jdbcTemplate.queryForList(prepared.getSql(), prepared.getParams());
        return shape(raw, prepared.getPageSize(), hideTechnicalColumns);
    }

    // Pure logic (no database), so it is easy to test:
    // cut the extra row, set hasNext, optionally drop technical columns, keep order.
    public static ExecutionResult shape(List<Map<String, Object>> raw, int pageSize, boolean hideTechnicalColumns) {
        // We asked for pageSize + 1 rows. If we got more than pageSize, there is a next page.
        boolean hasNext = raw.size() > pageSize;

        // Keep only the rows for this page (drop the extra one if it is there).
        List<Map<String, Object>> pageRows = hasNext
                ? new ArrayList<>(raw.subList(0, pageSize))
                : new ArrayList<>(raw);

        // Copy each row into a fresh map, skipping technical columns if asked.
        List<Map<String, Object>> cleanRows = new ArrayList<>(pageRows.size());
        for (Map<String, Object> row : pageRows) {
            LinkedHashMap<String, Object> clean = new LinkedHashMap<>();
            for (Map.Entry<String, Object> column : row.entrySet()) {
                if (hideTechnicalColumns && isTechnicalColumn(column.getKey())) {
                    continue;
                }
                clean.put(column.getKey(), column.getValue());
            }
            cleanRows.add(clean);
        }

        return new ExecutionResult(cleanRows, hasNext);
    }

    // A "technical" column is one used inside the query (for filtering/joining) but not
    // meant to be shown to the user: id, anything ending in _id, date helpers, etc.
    private static boolean isTechnicalColumn(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.equals("id")
                || lower.contains("_id")
                || lower.contains("from_date")
                || lower.contains("to_date")
                || lower.equals("dummy")
                || lower.equals("temp");
    }
}
