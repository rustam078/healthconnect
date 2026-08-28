package in.healthconnect.setting.controller;

import in.healthconnect.setting.dto.request.AppSettingRequest;
import in.healthconnect.setting.dto.response.AppSettingResponse;
import in.healthconnect.setting.service.SettingService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Manage application settings (API keys, model names, anything configurable).
//
// Secret values come back MASKED - the masking is done by AppSettingResponse, not here.
// No try/catch: GlobalExceptionHandler maps IllegalArgumentException -> 400 and
// ResourceNotFoundException -> 404.
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class AppSettingController {

    private final SettingService settingService;

    @PostMapping
    public ResponseEntity<ApiResponse<AppSettingResponse>> create(
            @RequestBody @Valid AppSettingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(settingService.create(request), "Setting created"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AppSettingResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(settingService.list()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AppSettingResponse>> getOne(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(settingService.get(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AppSettingResponse>> update(
            @PathVariable Integer id,
            @RequestBody @Valid AppSettingRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(settingService.update(id, request), "Setting updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Integer id) {
        settingService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Setting deleted"));
    }
}
