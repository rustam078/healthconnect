package in.healthconnect.widgetengine.controller;

import in.healthconnect.widgetengine.dto.request.ExecuteWidgetRequest;
import in.healthconnect.widgetengine.entity.WidgetFilter;
import in.healthconnect.widgetengine.service.WidgetFilterService;
import in.healthconnect.wrapper.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// The filter catalogue. A filter lives here once and any widget can point at it by id,
// instead of every widget carrying its own copy of "the list of doctors".
@RestController
@RequestMapping("/api/v1/widget-filters")
@RequiredArgsConstructor
public class WidgetFilterController {

    private final WidgetFilterService filterService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WidgetFilter>>> list() {
        return ResponseEntity.ok(ApiResponse.success(filterService.list(), "Filters retrieved successfully"));
    }

    // Create or replace one. The id is chosen by hand because it is what appears in SQL
    // as :name and in a widget's filter config.
    @PostMapping
    public ResponseEntity<ApiResponse<WidgetFilter>> save(@RequestBody WidgetFilter filter) {
        return ResponseEntity.ok(ApiResponse.success(filterService.save(filter), "Filter saved successfully"));
    }

    // The choices for a dropdown. Takes the same flat body a widget takes, so a list can
    // narrow itself by whatever its own query asks for.
    @PostMapping("/{id}/options")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> options(
            @PathVariable String id,
            @RequestBody(required = false) ExecuteWidgetRequest request) {
        ExecuteWidgetRequest safe = request == null ? new ExecuteWidgetRequest() : request;
        return ResponseEntity.ok(ApiResponse.success(
                filterService.options(id, safe.getParams(), safe.getPageSize()).rows(),
                "Options retrieved successfully"));
    }
}
