package in.healthconnect.widgetengine.prompt;

import in.healthconnect.widgetengine.engine.SqlSafetyGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// The home for the PROMPT module (plain English -> SQL).
// This is only a skeleton for now. It shows HOW the AI will fit in, and - importantly -
// that the AI's output must still pass our safety checks before we would ever run it.
@Service
@RequiredArgsConstructor
public class PromptService {

    private final QueryGenerator queryGenerator;
    private final SqlSafetyGuard safetyGuard;

    // Turn a plain-English question into a SAFE SQL query.
    public String generateSafeSql(String naturalLanguagePrompt) {

        // ========================================================================
        // THE AI CALL
        // The injected QueryGenerator is NimQueryGenerator, which sends
        // `naturalLanguagePrompt` to NVIDIA NIM and returns a MySQL query string.
        // Swapping providers means writing another QueryGenerator - nothing here changes.
        // ========================================================================
        String sql = queryGenerator.generateSql(naturalLanguagePrompt);

        // VERY IMPORTANT: never trust AI output. Whatever the AI returns must still be a
        // safe, read-only SELECT before we would run it (same gate as everywhere else).
        safetyGuard.assertSelectOnly(sql);

        // LATER: from here we could either
        //   (a) run this SQL straight away (like the INTEGRATION module), or
        //   (b) save it as a new PROMPT widget so the user can reuse it.
        return sql;
    }
}
