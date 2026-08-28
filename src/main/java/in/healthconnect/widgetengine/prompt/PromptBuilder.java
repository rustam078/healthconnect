package in.healthconnect.widgetengine.prompt;

import in.healthconnect.widgetengine.entity.AiKnowledge;
import in.healthconnect.widgetengine.entity.AiPromptExample;
import org.springframework.stereotype.Component;

import java.util.List;

// Builds ONE text prompt to send to the AI. Four parts:
//   1. RULES     - fixed instructions (MySQL, SELECT only, output format, self-check)
//   2. SCHEMA    - the tables/columns/hints from the knowledge base
//   3. EXAMPLES  - a few question -> SQL pairs to copy the right style
//   4. QUESTION  - the user's plain-English question
//
// Only part 4 changes each time. Parts 1-3 are the same, which is what lets us cache
// them later to save tokens. We keep everything compact on purpose (fewer tokens).
@Component
public class PromptBuilder {

    // Fixed rules. Adapted for MySQL and SELECT-only (based on a proven BigQuery prompt).
    private static final String RULES = """
            You are an expert MySQL query writer for the HealthConnect hospital database.
            Turn the user's plain-English question into ONE correct MySQL query.

            OUTPUT RULES:
            - Return exactly ONE MySQL SELECT query and nothing else.
            - No markdown, no code fences, no comments, no explanation.
            - Never write INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, TRUNCATE, GRANT or REVOKE.
            - Use single quotes for text values, e.g. name = 'Cardiology'.
            - Put backticks around a column alias ONLY if it has a space, e.g. AS `First Name`.

            COLUMN RULES:
            - Use ONLY the tables and columns listed in SCHEMA below. Never invent a column.
            - If a table has an is_deleted column, always add "is_deleted = false" for it.
            - Use the HINTS in SCHEMA to join tables correctly.

            MYSQL-ONLY RULES (do not use PostgreSQL or BigQuery syntax):
            - No ::cast, no ILIKE, no to_char, no date_trunc, no "interval '1 month'".
            - Case-insensitive text match: LOWER(col) LIKE LOWER('%text%').
            - Today's date is CURDATE(). Current date-time is NOW().

            FINAL CHECK before you answer:
            - It is a single SELECT only (no other statement types).
            - Every table and column you used appears in SCHEMA.
            - You added is_deleted = false for any table that has it.
            - No markdown and no comments in the output.
            """;

    public String build(String question, List<AiKnowledge> knowledge, List<AiPromptExample> examples) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(RULES);

        // ---- SCHEMA ----
        prompt.append("\nSCHEMA:\n");
        if (knowledge == null || knowledge.isEmpty()) {
            prompt.append("(no tables provided)\n");
        } else {
            for (AiKnowledge k : knowledge) {
                prompt.append("- TABLE ").append(k.getTableName());
                if (hasText(k.getPurpose())) {
                    prompt.append(" : ").append(k.getPurpose());
                }
                prompt.append("\n");
                if (hasText(k.getColumnsInfo())) {
                    prompt.append("  columns: ").append(k.getColumnsInfo()).append("\n");
                }
                if (hasText(k.getHints())) {
                    prompt.append("  hints: ").append(k.getHints()).append("\n");
                }
            }
        }

        // ---- EXAMPLES ----
        if (examples != null && !examples.isEmpty()) {
            prompt.append("\nEXAMPLES:\n");
            for (AiPromptExample e : examples) {
                prompt.append("Q: ").append(e.getQuestion()).append("\n");
                prompt.append("SQL:\n").append(e.getGeneratedSql()).append("\n\n");
            }
        }

        // ---- QUESTION ----
        prompt.append("\nUSER QUESTION:\n").append(question).append("\n");
        prompt.append("SQL:");

        return prompt.toString();
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
