package in.healthconnect.widgetengine.prompt;

import org.springframework.stereotype.Component;

// Cleans up the SQL text the AI returns, so it is a plain query we can store and run.
// AI models often wrap the SQL in ```sql ... ``` fences and/or add a trailing ";".
@Component
public class SqlCleaner {

    public String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String sql = raw.trim();

        // remove an opening code fence like ```sql or ``` (with any following spaces/newlines)
        sql = sql.replaceAll("(?is)^```[a-z]*\\s*", "");
        // remove a closing code fence at the end
        sql = sql.replaceAll("(?is)\\s*```$", "");
        sql = sql.trim();

        // remove a trailing ";" - the engine adds ORDER BY / LIMIT after the query,
        // so a semicolon in the middle would break it
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        return sql;
    }
}
