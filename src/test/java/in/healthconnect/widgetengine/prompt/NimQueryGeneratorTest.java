package in.healthconnect.widgetengine.prompt;

import in.healthconnect.exception.AiProviderException;
import in.healthconnect.exception.SettingNotConfiguredException;
import in.healthconnect.setting.service.SettingService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Tests for NimQueryGenerator - the parts we can check WITHOUT calling the network:
//   1. no API key configured => fail clearly, naming the setting to add;
//   2. read the SQL out of NIM's OpenAI-compatible JSON response;
//   3. a malformed response fails loudly instead of returning null.
//
// The real HTTP call is exercised by hand once a key is in the settings table.
class NimQueryGeneratorTest {

    private final SettingService settingService = mock(SettingService.class);
    private final NimQueryGenerator generator =
            new NimQueryGenerator(JsonMapper.builder().build(), settingService);

    @Test
    void failsClearlyWhenApiKeyIsNotConfigured() {
        when(settingService.getRequired("nim.api-key"))
                .thenThrow(new SettingNotConfiguredException(
                        "Setting 'nim.api-key' is not configured. Add it under /api/v1/settings."));

        SettingNotConfiguredException error = assertThrows(SettingNotConfiguredException.class,
                () -> generator.generateSql("count doctors"));

        assertTrue(error.getMessage().contains("nim.api-key"));
    }

    @Test
    void extractsSqlTextFromNimResponse() {
        String responseJson =
                "{ \"choices\": [ { \"index\": 0, \"message\": { \"role\": \"assistant\", " +
                "\"content\": \"SELECT count(*) FROM doctors\" } } ] }";

        assertEquals("SELECT count(*) FROM doctors", generator.extractText(responseJson));
    }

    @Test
    void throwsWhenResponseHasNoContent() {
        String responseJson = "{ \"choices\": [ { \"message\": { \"role\": \"assistant\" } } ] }";

        assertThrows(AiProviderException.class, () -> generator.extractText(responseJson));
    }

    @Test
    void throwsWhenChoicesIsEmpty() {
        assertThrows(AiProviderException.class, () -> generator.extractText("{ \"choices\": [] }"));
    }
}
