package in.healthconnect.widgetengine.prompt;

import in.healthconnect.setting.service.SettingService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

// The REAL AI. Sends the prompt to NVIDIA NIM and returns the SQL it writes.
//
// NIM speaks the OpenAI-compatible chat API, so this is a plain POST to /chat/completions
// with a Bearer token.
//
// Credentials come from the app_setting table (NOT application.properties), so they can be
// changed over HTTP without a restart and are never committed to git. If no key is
// configured, getRequired throws a clear error naming the setting to add.
//
// Whatever comes back is still untrusted: SqlDraftService cleans it (SqlCleaner) and
// SqlSafetyGuard rejects anything that is not a single SELECT.
@Component
public class NimQueryGenerator implements QueryGenerator {

    static final String KEY_SETTING = "nim.api-key";
    static final String MODEL_SETTING = "nim.model";
    static final String BASE_URL_SETTING = "nim.base-url";

    static final String DEFAULT_MODEL = "qwen/qwen2.5-coder-32b-instruct";
    static final String DEFAULT_BASE_URL = "https://integrate.api.nvidia.com/v1";

    private final ObjectMapper objectMapper;
    private final SettingService settingService;

    public NimQueryGenerator(ObjectMapper objectMapper, SettingService settingService) {
        this.objectMapper = objectMapper;
        this.settingService = settingService;
    }

    @Override
    public String generateSql(String prompt) {
        // Only the key is mandatory; model and base URL fall back to sensible defaults.
        String apiKey = settingService.getRequired(KEY_SETTING);
        String model = settingService.getOrDefault(MODEL_SETTING, DEFAULT_MODEL);
        String baseUrl = settingService.getOrDefault(BASE_URL_SETTING, DEFAULT_BASE_URL);

        // temperature 0 + low top_p => as predictable as possible: the same question
        // should give the same SQL. PromptBuilder already bakes the RULES into the prompt,
        // so a single user message is all that is needed.
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0,
                "top_p", 0.1,
                "max_tokens", 1024,
                "stream", false
        );

        String response = RestClient.create()
                .post()
                .uri(baseUrl + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        return extractText(response);
    }

    // Read choices[0].message.content out of NIM's JSON reply.
    String extractText(String responseJson) {
        JsonNode content = objectMapper.readTree(responseJson)
                .path("choices").path(0)
                .path("message").path("content");

        if (content.isMissingNode() || content.isNull()) {
            throw new RuntimeException("NIM response did not contain any SQL text.");
        }
        return content.asString();
    }
}
