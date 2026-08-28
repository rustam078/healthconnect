package in.healthconnect.widgetengine.controller;

import in.healthconnect.widgetengine.dto.request.AiPromptExampleRequest;
import in.healthconnect.widgetengine.dto.response.AiPromptExampleResponse;
import in.healthconnect.widgetengine.service.KnowledgeBaseService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// UI endpoints to manage the AI EXAMPLES (question -> correct SQL).
// Good examples make the AI's answers much more accurate.
@RestController
@RequestMapping("/api/v1/ai/examples")
@RequiredArgsConstructor
public class AiPromptExampleController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping
    public ResponseEntity<ApiResponse<AiPromptExampleResponse>> create(
            @RequestBody @Valid AiPromptExampleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(knowledgeBaseService.createExample(request), "Example added"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AiPromptExampleResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(knowledgeBaseService.listExamples()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AiPromptExampleResponse>> getOne(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(knowledgeBaseService.getExample(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AiPromptExampleResponse>> update(
            @PathVariable Integer id,
            @RequestBody @Valid AiPromptExampleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(knowledgeBaseService.updateExample(id, request), "Example updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Integer id) {
        knowledgeBaseService.deleteExample(id);
        return ResponseEntity.ok(ApiResponse.success("Example deleted"));
    }
}
