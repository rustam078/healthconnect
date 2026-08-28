package in.healthconnect.widgetengine.prompt;

import in.healthconnect.widgetengine.entity.AiKnowledge;
import in.healthconnect.widgetengine.entity.AiPromptExample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests for PromptBuilder.
// It builds ONE text prompt from: rules + schema knowledge + examples + the user's question.
// We check the important parts are present and that it is MySQL/SELECT-only focused.
class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    private AiKnowledge knowledge(String table, String purpose, String columns, String hints) {
        AiKnowledge k = new AiKnowledge();
        k.setTableName(table);
        k.setPurpose(purpose);
        k.setColumnsInfo(columns);
        k.setHints(hints);
        k.setEnabled(true);
        return k;
    }

    private AiPromptExample example(String question, String sql) {
        AiPromptExample e = new AiPromptExample();
        e.setQuestion(question);
        e.setGeneratedSql(sql);
        e.setEnabled(true);
        return e;
    }

    @Test
    void includesTheUserQuestion() {
        String prompt = builder.build("list all cardiology doctors", List.of(), List.of());
        assertTrue(prompt.contains("list all cardiology doctors"));
    }

    @Test
    void includesSchemaTablesColumnsAndHints() {
        String prompt = builder.build(
                "count doctors",
                List.of(knowledge("doctors", "Doctors in the hospital",
                        "id, first_name, last_name, is_deleted",
                        "join to specialties via doctor_specialties_map")),
                List.of());

        assertTrue(prompt.contains("doctors"));                       // table name
        assertTrue(prompt.contains("first_name"));                    // a column
        assertTrue(prompt.contains("doctor_specialties_map"));        // the hint
    }

    @Test
    void includesExamples() {
        String prompt = builder.build(
                "count doctors",
                List.of(),
                List.of(example("count all doctors",
                        "SELECT count(*) AS total FROM doctors WHERE is_deleted = false")));

        assertTrue(prompt.contains("count all doctors"));             // example question
        assertTrue(prompt.contains("SELECT count(*) AS total"));      // example SQL
    }

    @Test
    void statesSelectOnlyAndMysqlRules() {
        String prompt = builder.build("anything", List.of(), List.of());
        String lower = prompt.toLowerCase();
        assertTrue(lower.contains("select"));      // must mention SELECT-only
        assertTrue(lower.contains("mysql"));       // must mention MySQL
        // must forbid other statement types somewhere in the rules
        assertTrue(prompt.contains("INSERT") && prompt.contains("DELETE"));
    }

    @Test
    void forbidsMarkdownFencesInOutput() {
        String prompt = builder.build("anything", List.of(), List.of());
        assertTrue(prompt.toLowerCase().contains("no markdown")
                || prompt.toLowerCase().contains("without markdown")
                || prompt.toLowerCase().contains("no fences"));
    }
}
