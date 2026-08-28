package in.healthconnect.widgetengine.controller;

import in.healthconnect.widgetengine.dto.request.DryRunWidgetRequest;
import in.healthconnect.widgetengine.dto.request.ExecuteWidgetRequest;
import in.healthconnect.widgetengine.dto.response.WidgetDataResponse;
import in.healthconnect.widgetengine.service.WidgetExecutionService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// The endpoints that actually RUN a widget and return its data.
// Two doors into the same engine:
//   - /api/v1/widgets/{idOrCode}/data  -> for dashboard widgets (hides technical columns)
//   - /api/v1/integration/{code}       -> the "API replacement" (returns all columns)
@RestController
@RequiredArgsConstructor
public class WidgetExecutionController {

    private final WidgetExecutionService executionService;

    // Run a WIDGET and return its data (with filters + paging from the request body).
    // We hide technical columns (like id) because this is meant for display.
    @PostMapping("/api/v1/widgets/{idOrCode}/data")
    public ResponseEntity<ApiResponse<WidgetDataResponse>> getData(
            @PathVariable String idOrCode,
            @RequestBody(required = false) ExecuteWidgetRequest request) {
        ExecuteWidgetRequest safeRequest = request == null ? new ExecuteWidgetRequest() : request;
        WidgetDataResponse data = executionService.execute(idOrCode, safeRequest, true);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // Run a query that has NOT been saved as a widget yet. Nothing is stored.
    //
    // This is the developer's "does it work?" button when writing a widget by hand:
    // unlike /data it returns the database's real complaint, because the person calling
    // it wrote the query and cannot fix what they cannot see.
    @PostMapping("/api/v1/widgets/dry-run")
    public ResponseEntity<ApiResponse<WidgetDataResponse>> dryRun(
            @RequestBody @Valid DryRunWidgetRequest request) {
        return ResponseEntity.ok(ApiResponse.success(executionService.dryRun(request)));
    }

    // Run an INTEGRATION widget by its code - a stored query used as a simple API.
    // We keep all columns here (no hiding), since callers may need id fields.
    @PostMapping("/api/v1/integration/{code}")
    public ResponseEntity<ApiResponse<WidgetDataResponse>> integration(
            @PathVariable String code,
            @RequestBody(required = false) ExecuteWidgetRequest request) {
        ExecuteWidgetRequest safeRequest = request == null ? new ExecuteWidgetRequest() : request;
        WidgetDataResponse data = executionService.execute(code, safeRequest, false);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
