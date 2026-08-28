package in.healthconnect.widgetengine.controller;

import in.healthconnect.widgetengine.dto.request.AiKnowledgeRequest;
import in.healthconnect.widgetengine.dto.response.AiKnowledgeResponse;
import in.healthconnect.widgetengine.service.KnowledgeBaseService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// UI endpoints to manage the AI KNOWLEDGE (one row per table).
// This is how you tell the AI what tables exist and what they mean.
@RestController
@RequestMapping("/api/v1/ai/knowledge")
@RequiredArgsConstructor
public class AiKnowledgeController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping
    public ResponseEntity<ApiResponse<AiKnowledgeResponse>> create(
            @RequestBody @Valid AiKnowledgeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(knowledgeBaseService.createKnowledge(request), "Knowledge added"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AiKnowledgeResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(knowledgeBaseService.listKnowledge()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AiKnowledgeResponse>> getOne(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(knowledgeBaseService.getKnowledge(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AiKnowledgeResponse>> update(
            @PathVariable Integer id,
            @RequestBody @Valid AiKnowledgeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(knowledgeBaseService.updateKnowledge(id, request), "Knowledge updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Integer id) {
        knowledgeBaseService.deleteKnowledge(id);
        return ResponseEntity.ok(ApiResponse.success("Knowledge deleted"));
    }
}
