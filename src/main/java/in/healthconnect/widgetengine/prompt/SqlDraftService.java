package in.healthconnect.widgetengine.prompt;

import in.healthconnect.widgetengine.dto.response.GeneratedQueryResponse;
import in.healthconnect.widgetengine.engine.SqlSafetyGuard;
import in.healthconnect.widgetengine.entity.AiKnowledge;
import in.healthconnect.widgetengine.entity.AiPromptExample;
import in.healthconnect.widgetengine.entity.Widget;
import in.healthconnect.widgetengine.entity.enums.WidgetModule;
import in.healthconnect.widgetengine.entity.enums.WidgetStatus;
import in.healthconnect.widgetengine.entity.enums.WidgetType;
import in.healthconnect.widgetengine.repository.AiKnowledgeRepository;
import in.healthconnect.widgetengine.repository.AiPromptExampleRepository;
import in.healthconnect.widgetengine.repository.WidgetRepository;
import org.springframework.stereotype.Service;

import java.util.List;

// Turns a plain-English question into a stored DRAFT widget. Steps:
//   1. If the question EXACTLY matches a saved example, reuse that SQL (no AI call - saves tokens).
//   2. Otherwise: build the prompt (knowledge + examples + question) and ask the AI.
//   3. Clean the AI's answer (strip code fences etc.).
//   4. Check it is a safe SELECT (SqlSafetyGuard).
//   5. Save it as a PROMPT widget marked DRAFT (so a person reviews it before it's trusted).
@Service
public class SqlDraftService {

    private final AiKnowledgeRepository knowledgeRepository;
    private final AiPromptExampleRepository exampleRepository;
    private final WidgetRepository widgetRepository;
    private final PromptBuilder promptBuilder;
    private final SqlCleaner sqlCleaner;
    private final SqlSafetyGuard safetyGuard;
    private final QueryGenerator queryGenerator;

    public SqlDraftService(AiKnowledgeRepository knowledgeRepository,
                           AiPromptExampleRepository exampleRepository,
                           WidgetRepository widgetRepository,
                           PromptBuilder promptBuilder,
                           SqlCleaner sqlCleaner,
                           SqlSafetyGuard safetyGuard,
                           QueryGenerator queryGenerator) {
        this.knowledgeRepository = knowledgeRepository;
        this.exampleRepository = exampleRepository;
        this.widgetRepository = widgetRepository;
        this.promptBuilder = promptBuilder;
        this.sqlCleaner = sqlCleaner;
        this.safetyGuard = safetyGuard;
        this.queryGenerator = queryGenerator;
    }

    // `title` is optional: it becomes the heading shown on the widget. When it is blank we
    // fall back to the question, which is what the widget was always named before.
    public GeneratedQueryResponse generateDraft(String question, String title) {
        List<AiPromptExample> examples = exampleRepository.findByEnabledTrue();

        // 1. exact-match reuse (cheap + reliable)
        String sql = findExactExampleSql(question, examples);

        // 2. otherwise ask the AI
        if (sql == null) {
            List<AiKnowledge> knowledge = knowledgeRepository.findByEnabledTrueOrderByTableNameAsc();
            String prompt = promptBuilder.build(question, knowledge, examples);
            String raw = queryGenerator.generateSql(prompt);
            // 3. clean the answer
            sql = sqlCleaner.clean(raw);
        }

        // 4. safety check (never trust AI output)
        safetyGuard.assertSelectOnly(sql);

        // 5. store as a DRAFT PROMPT widget
        Widget widget = new Widget();
        widget.setCode(uniqueCode(question));
        // name = what a person sees on the card; description = what was actually asked.
        widget.setName(displayName(question, title));
        widget.setDescription(question);
        widget.setModule(WidgetModule.PROMPT);
        widget.setType(WidgetType.TABLE);
        widget.setSqlTemplate(sql);
        widget.setEnabled(true);
        widget.setStatus(WidgetStatus.DRAFT);
        // no filter settings for an AI draft yet
        widget.setFilters(null);

        return GeneratedQueryResponse.of(widgetRepository.save(widget));
    }


    // The heading to show on the widget: the title if one was given, otherwise the
    // question. Trimmed to the column's 200 characters either way.
    private String displayName(String question, String title) {
        String chosen = (title == null || title.isBlank()) ? question : title.trim();
        return trim(chosen, 200);
    }
    // Find a saved example whose question matches (ignoring case and extra spaces).
    private String findExactExampleSql(String question, List<AiPromptExample> examples) {
        String normalized = normalize(question);
        for (AiPromptExample example : examples) {
            if (normalize(example.getQuestion()).equals(normalized)) {
                return example.getGeneratedSql();
            }
        }
        return null;
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    // Make a readable, unique code from the question, e.g. "count all doctors" -> "count-all-doctors".
    private String uniqueCode(String question) {
        String base = normalize(question).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "ai-query";
        }
        base = trim(base, 120);
        String code = base;
        int counter = 2;
        // NOTE: existsByCodeIncludingDeleted, not existsByCode. Soft-deleted widgets still
        // occupy their code in the unique index, so ignoring them here causes a
        // duplicate-key error on insert.
        while (widgetRepository.countByCodeIncludingDeleted(code) > 0) {
            code = base + "-" + counter++;
        }
        return code;
    }

    private String trim(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}
