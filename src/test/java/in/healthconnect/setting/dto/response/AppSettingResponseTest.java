package in.healthconnect.setting.dto.response;

import in.healthconnect.setting.entity.AppSetting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// The masking rule lives in AppSettingResponse.from(...) on purpose: if it lived in the
// controller, any future endpoint could leak a secret by forgetting to call it.
class AppSettingResponseTest {

    private AppSetting setting(String value, boolean secret) {
        AppSetting s = new AppSetting();
        s.setName("nim.api-key");
        s.setValue(value);
        s.setSecret(secret);
        s.setEnabled(true);
        return s;
    }

    @Test
    void nonSecretValuePassesThroughUnchanged() {
        AppSettingResponse response =
                AppSettingResponse.from(setting("qwen/qwen2.5-coder-32b-instruct", false));

        assertEquals("qwen/qwen2.5-coder-32b-instruct", response.getValue());
    }

    @Test
    void longSecretShowsOnlyFirstAndLastFour() {
        // 20 characters: "nvap" + "****" + "3f2a"
        AppSettingResponse response = AppSettingResponse.from(setting("nvapi-abcdefghij3f2a", true));

        assertEquals("nvap****3f2a", response.getValue());
    }

    @Test
    void shortSecretRevealsNothing() {
        // 8 characters or fewer: showing 4 + 4 would show the whole thing
        AppSettingResponse response = AppSettingResponse.from(setting("12345678", true));

        assertEquals("****", response.getValue());
    }

    @Test
    void blankOrNullSecretBecomesNull() {
        assertNull(AppSettingResponse.from(setting("   ", true)).getValue());
        assertNull(AppSettingResponse.from(setting(null, true)).getValue());
    }

    @Test
    void secretFlagIsReportedToTheClient() {
        assertTrue(AppSettingResponse.from(setting("nvapi-abcdefghij3f2a", true)).getSecret());
        assertFalse(AppSettingResponse.from(setting("plain", false)).getSecret());
    }
}
