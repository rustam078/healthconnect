package in.healthconnect.widgetengine.prompt;

import in.healthconnect.exception.AiProviderException;
import in.healthconnect.setting.service.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
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
// Model availability changes: NIM retires models, and a model listed in GET /v1/models
// can still 404 if it is not provisioned for your account. Because the model is a setting,
// swapping it is a PUT to /api/v1/settings - never a code change.
//
// Whatever comes back is still untrusted: SqlDraftService cleans it (SqlCleaner) and
// SqlSafetyGuard rejects anything that is not a single SELECT.
@Component
public class NimQueryGenerator implements QueryGenerator {

    static final String KEY_SETTING = "nim.api-key";
    static final String MODEL_SETTING = "nim.model";
    static final String BASE_URL_SETTING = "nim.base-url";

    static final String DEFAULT_MODEL = "nvidia/nemotron-3-super-120b-a12b";
    static final String DEFAULT_BASE_URL = "https://integrate.api.nvidia.com/v1";

    private static final Logger log = LoggerFactory.getLogger(NimQueryGenerator.class);

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

        String response;
        try {
            response = RestClient.create()
                    .post()
                    .uri(baseUrl + "/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            // NIM said no. Its body explains why (bad key, unknown model, bad parameter),
            // and without this the caller only ever sees a generic 500.
            // The API key is never logged.
            log.error("NIM call failed: model={} status={} body={}",
                    model, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new AiProviderException(
                    "The AI provider rejected the request (" + ex.getStatusCode() + "). "
                            + "Check the nim.model and nim.api-key settings.", ex);
        } catch (RestClientException ex) {
            // Never reached the provider at all, or it did not answer in time. This is a
            // DIFFERENT exception type from the one above - a timeout is not an HTTP error
            // response - and without this branch it fell through as a bare 500.
            log.error("NIM call did not complete: model={} cause={}", model, ex.toString());
            throw new AiProviderException(
                    "Could not reach the AI provider (" + rootCauseOf(ex) + "). "
                            + "It may be slow or unavailable - try again.", ex);
        }

        return extractText(response);
    }


    // The innermost cause, which is what actually says "timed out" or "connection refused".
    private static String rootCauseOf(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
    // Read choices[0].message.content out of NIM's JSON reply.
    String extractText(String responseJson) {
        JsonNode content = objectMapper.readTree(responseJson)
                .path("choices").path(0)
                .path("message").path("content");

        if (content.isMissingNode() || content.isNull()) {
            throw new AiProviderException("NIM response did not contain any SQL text.");
        }
        return content.asString();
    }
}
