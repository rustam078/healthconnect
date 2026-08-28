package in.healthconnect.widgetengine.controller;

import in.healthconnect.widgetengine.dto.request.CreateWidgetRequest;
import in.healthconnect.widgetengine.dto.request.ExecuteWidgetRequest;
import in.healthconnect.widgetengine.dto.request.UpdateWidgetRequest;
import in.healthconnect.widgetengine.dto.response.PreviewResponse;
import in.healthconnect.widgetengine.dto.response.WidgetResponse;
import in.healthconnect.widgetengine.dto.response.WidgetSummaryResponse;
import in.healthconnect.widgetengine.engine.PreparedQuery;
import in.healthconnect.widgetengine.entity.enums.WidgetModule;
import in.healthconnect.widgetengine.service.WidgetExecutionService;
import in.healthconnect.widgetengine.service.WidgetService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// The admin endpoints for MANAGING widgets (create/read/update/delete) + a preview.
// Everything is wrapped in ApiResponse, the same as the rest of the app.
// Errors (not found, bad input) are handled by the project's GlobalExceptionHandler,
// so we don't need try/catch here.
@RestController
@RequestMapping("/api/v1/widgets")
@RequiredArgsConstructor
public class WidgetController {

    private final WidgetService widgetService;
    private final WidgetExecutionService executionService;

    // Create a new widget.
    @PostMapping
    public ResponseEntity<ApiResponse<WidgetResponse>> create(
            @RequestBody @Valid CreateWidgetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(widgetService.create(request), "Widget created successfully"));
    }

    // List widgets, one page at a time. Optionally filter by module (e.g. WIDGET) -
    // this is what powers the "suggested widgets" list when building a board.
    @GetMapping
    public ResponseEntity<ApiResponse<Page<WidgetSummaryResponse>>> list(
            @RequestParam(required = false) WidgetModule module,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(widgetService.list(module, pageable), "Widgets retrieved successfully"));
    }

    // Get one widget by its id OR its code.
    @GetMapping("/{idOrCode}")
    public ResponseEntity<ApiResponse<WidgetResponse>> getOne(@PathVariable String idOrCode) {
        return ResponseEntity.ok(ApiResponse.success(widgetService.getByIdOrCode(idOrCode)));
    }

    // Update a widget by id.
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WidgetResponse>> update(
            @PathVariable Integer id,
            @RequestBody @Valid UpdateWidgetRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(widgetService.update(id, request), "Widget updated successfully"));
    }

    // Delete a widget by id (soft delete).
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Integer id) {
        widgetService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Widget deleted successfully"));
    }

    // Approve a DRAFT widget (usually an AI-generated one) so it is ready for normal use.
    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<WidgetResponse>> approve(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(widgetService.approve(id), "Widget approved"));
    }

    // Preview the FINAL query for a widget WITHOUT running it (great for learning/debugging).
    @PostMapping("/{idOrCode}/preview")
    public ResponseEntity<ApiResponse<PreviewResponse>> preview(
            @PathVariable String idOrCode,
            @RequestBody(required = false) ExecuteWidgetRequest request) {
        ExecuteWidgetRequest safeRequest = request == null ? new ExecuteWidgetRequest() : request;
        PreparedQuery prepared = executionService.buildPreview(idOrCode, safeRequest);
        return ResponseEntity.ok(ApiResponse.success(PreviewResponse.of(prepared)));
    }
}
