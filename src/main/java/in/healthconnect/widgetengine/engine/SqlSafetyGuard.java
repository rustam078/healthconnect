package in.healthconnect.widgetengine.engine;

import in.healthconnect.widgetengine.entity.enums.FilterOperator;
import org.springframework.stereotype.Component;

// This class is the "security gate" for queries.
// A widget stores a query written by an admin. Before we ever run it, we check:
//   1. It only READS data (starts with SELECT or WITH). It must not change or delete data.
//   2. It is a SINGLE command (no sneaky second command hidden after a ";").
//   3. It does not try to write results to a file on the server.
// We also check that a filter's operator (like "in") is one we allow.
@Component
public class SqlSafetyGuard {

    // Returns true if the query is a safe, read-only SELECT. Otherwise false.
    public boolean isSelectOnly(String sql) {
        // No query at all -> not safe.
        if (sql == null || sql.isBlank()) {
            return false;
        }

        String trimmed = sql.trim();

        // A single ";" at the very end is fine (some people add it). Remove it once.
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }

        // Hide any text that sits inside quotes, e.g. 'hello;world', so punctuation
        // inside a normal text value does not confuse the checks below.
        String scrubbed = blankStringLiterals(trimmed);

        // After removing the one allowed end ";", there must be NO more ";".
        // A leftover ";" means someone tried to add a second, hidden command.
        if (scrubbed.contains(";")) {
            return false;
        }

        String lower = scrubbed.toLowerCase();

        // The query must START with the word "select" or "with" (nothing else).
        // "\\b" means "word boundary" so we match the whole word, not part of another word.
        if (!lower.matches("(?s)^(select|with)\\b.*")) {
            return false;
        }

        // MySQL can save query results to a file on the server. We never allow that.
        if (lower.contains("into outfile") || lower.contains("into dumpfile")) {
            return false;
        }

        return true;
    }

    // Same check, but instead of returning false it throws a clear error.
    // Use this when you want to STOP and report a bad query.
    public void assertSelectOnly(String sql) {
        if (!isSelectOnly(sql)) {
            throw new IllegalArgumentException("Only read-only SELECT queries are allowed.");
        }
    }

    // Turn a filter operator word (like "in") into a known operator.
    // If the word is not in our allowed list, throw a clear error.
    public FilterOperator requireOperator(String key) {
        return FilterOperator.fromKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown filter operator: " + key));
    }

    // Replace anything inside single quotes with empty quotes ''.
    // Example:  name = 'a;b'   becomes   name = ''
    // This way, punctuation inside a text value is ignored by our safety checks.
    private String blankStringLiterals(String sql) {
        return sql.replaceAll("'[^']*'", "''");
    }
}
