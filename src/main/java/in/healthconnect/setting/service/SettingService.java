package in.healthconnect.setting.service;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.exception.SettingNotConfiguredException;
import in.healthconnect.setting.dto.request.AppSettingRequest;
import in.healthconnect.setting.dto.response.AppSettingResponse;
import in.healthconnect.setting.entity.AppSetting;
import in.healthconnect.setting.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Application settings: plain CRUD for clients, plus two readers for server-side code.
//
// The split matters. create/list/get/update return AppSettingResponse, which MASKS secret
// values. getRequired/getOrDefault return the RAW value and are only ever called from
// inside the server (e.g. NimQueryGenerator reading the API key).
@Service
@RequiredArgsConstructor
public class SettingService {

    private final AppSettingRepository repository;

    // ---------- CRUD (masked on the way out) ----------

    public AppSettingResponse create(AppSettingRequest request) {
        // Checked here rather than with @NotBlank, because the annotation would also apply
        // to update - where a missing value deliberately means "keep the stored one".
        if (request.getValue() == null || request.getValue().isBlank()) {
            throw new IllegalArgumentException("A value is required when creating a setting.");
        }
        if (repository.existsByName(request.getName())) {
            throw new IllegalArgumentException(
                    "Setting '" + request.getName() + "' already exists.");
        }
        AppSetting setting = new AppSetting();
        setting.setName(request.getName());
        setting.setValue(request.getValue());
        setting.setSecret(request.getSecret() == null ? Boolean.FALSE : request.getSecret());
        setting.setDescription(request.getDescription());
        setting.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        return AppSettingResponse.from(repository.save(setting));
    }

    // Returns every non-deleted row, INCLUDING disabled ones, so a disabled setting stays
    // visible and can be switched back on.
    @Transactional(readOnly = true)
    public List<AppSettingResponse> list() {
        return repository.findAll().stream()
                .map(AppSettingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AppSettingResponse get(Integer id) {
        return AppSettingResponse.from(find(id));
    }

    public AppSettingResponse update(Integer id, AppSettingRequest request) {
        AppSetting setting = find(id);
        // The name is deliberately NOT updated - other code looks settings up by name.
        // A null value means "leave it as it is", which is how the settings screen can turn
        // a secret setting on or off without ever having seen the key it is turning off.
        if (request.getValue() != null) {
            setting.setValue(request.getValue());
        }
        setting.setDescription(request.getDescription());
        if (request.getSecret() != null) {
            setting.setSecret(request.getSecret());
        }
        if (request.getEnabled() != null) {
            setting.setEnabled(request.getEnabled());
        }
        return AppSettingResponse.from(repository.save(setting));
    }

    public void delete(Integer id) {
        repository.delete(find(id)); // soft delete via @SQLDelete
    }

    // ---------- server-side readers (REAL value, never masked, never serialized) ----------

    // The value, or a clear error naming the setting that needs configuring.
    @Transactional(readOnly = true)
    public String getRequired(String name) {
        String value = rawValue(name);
        if (value == null) {
            throw new SettingNotConfiguredException(
                    "Setting '" + name + "' is not configured. Add it under /api/v1/settings.");
        }
        return value;
    }

    // The value, or the fallback when it is missing/disabled/blank.
    @Transactional(readOnly = true)
    public String getOrDefault(String name, String fallback) {
        String value = rawValue(name);
        return value == null ? fallback : value;
    }

    // null when the row is missing, switched off, or holds nothing useful.
    private String rawValue(String name) {
        return repository.findByName(name)
                .filter(setting -> Boolean.TRUE.equals(setting.getEnabled()))
                .map(AppSetting::getValue)
                .filter(value -> !value.isBlank())
                .orElse(null);
    }

    private AppSetting find(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Setting not found: " + id));
    }
}
