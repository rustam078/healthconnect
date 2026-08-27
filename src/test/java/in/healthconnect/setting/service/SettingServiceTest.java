package in.healthconnect.setting.service;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.setting.dto.request.AppSettingRequest;
import in.healthconnect.setting.dto.response.AppSettingResponse;
import in.healthconnect.setting.entity.AppSetting;
import in.healthconnect.setting.repository.AppSettingRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Tests for SettingService. The repository is mocked, so no database is needed.
//
// The important rule under test: getRequired returns the REAL value (server-side reader),
// while anything that goes back to a client goes through AppSettingResponse, which masks.
class SettingServiceTest {

    private final AppSettingRepository repository = mock(AppSettingRepository.class);
    private final SettingService service = new SettingService(repository);

    private AppSettingRequest request(String name, String value) {
        AppSettingRequest r = new AppSettingRequest();
        r.setName(name);
        r.setValue(value);
        return r;
    }

    private AppSetting stored(String name, String value, boolean enabled) {
        AppSetting s = new AppSetting();
        s.setName(name);
        s.setValue(value);
        s.setEnabled(enabled);
        s.setSecret(false);
        return s;
    }

    @Test
    void createRejectsDuplicateName() {
        when(repository.existsByName("nim.api-key")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.create(request("nim.api-key", "nvapi-x")));
    }

    @Test
    void createAppliesDefaults() {
        when(repository.existsByName(any())).thenReturn(false);
        when(repository.save(any(AppSetting.class))).thenAnswer(call -> call.getArgument(0));

        AppSettingResponse response =
                service.create(request("nim.model", "qwen/qwen2.5-coder-32b-instruct"));

        assertTrue(response.getEnabled());   // defaults to on
        assertFalse(response.getSecret());   // defaults to not-secret
        assertEquals("qwen/qwen2.5-coder-32b-instruct", response.getValue()); // not secret -> not masked
        verify(repository).save(any(AppSetting.class));
    }

    @Test
    void getRequiredReturnsTheRealUnmaskedValue() {
        AppSetting secretRow = stored("nim.api-key", "nvapi-abcdefghij3f2a", true);
        secretRow.setSecret(true);
        when(repository.findByName("nim.api-key")).thenReturn(Optional.of(secretRow));

        assertEquals("nvapi-abcdefghij3f2a", service.getRequired("nim.api-key"));
    }

    @Test
    void getRequiredThrowsWhenMissing() {
        when(repository.findByName("nim.api-key")).thenReturn(Optional.empty());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.getRequired("nim.api-key"));
        assertTrue(error.getMessage().contains("nim.api-key"));
    }

    @Test
    void getRequiredThrowsWhenDisabled() {
        when(repository.findByName("nim.api-key"))
                .thenReturn(Optional.of(stored("nim.api-key", "nvapi-x", false)));

        assertThrows(IllegalStateException.class, () -> service.getRequired("nim.api-key"));
    }

    @Test
    void getRequiredThrowsWhenValueIsBlank() {
        when(repository.findByName("nim.api-key"))
                .thenReturn(Optional.of(stored("nim.api-key", "   ", true)));

        assertThrows(IllegalStateException.class, () -> service.getRequired("nim.api-key"));
    }

    @Test
    void getOrDefaultFallsBackWhenMissing() {
        when(repository.findByName("nim.model")).thenReturn(Optional.empty());

        assertEquals("fallback", service.getOrDefault("nim.model", "fallback"));
    }

    @Test
    void getOrDefaultReturnsTheValueWhenPresent() {
        when(repository.findByName("nim.model"))
                .thenReturn(Optional.of(stored("nim.model", "meta/llama-3.3-70b-instruct", true)));

        assertEquals("meta/llama-3.3-70b-instruct", service.getOrDefault("nim.model", "fallback"));
    }

    @Test
    void updateChangesTheValueButNeverTheName() {
        when(repository.findById(5)).thenReturn(Optional.of(stored("nim.model", "old-model", true)));
        when(repository.save(any(AppSetting.class))).thenAnswer(call -> call.getArgument(0));

        AppSettingResponse response = service.update(5, request("renamed", "new-model"));

        assertEquals("nim.model", response.getName());  // the name in the request is ignored
        assertEquals("new-model", response.getValue());
    }

    @Test
    void getThrowsWhenMissing() {
        when(repository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(99));
    }

    @Test
    void deleteRemoves() {
        AppSetting existing = stored("nim.model", "x", true);
        when(repository.findById(3)).thenReturn(Optional.of(existing));

        service.delete(3);

        verify(repository).delete(existing);  // soft delete happens via @SQLDelete
    }
}
