package in.healthconnect.widgetengine.controller;

import in.healthconnect.widgetengine.dto.request.GenerateQueryRequest;
import in.healthconnect.widgetengine.dto.response.GeneratedQueryResponse;
import in.healthconnect.widgetengine.prompt.SqlDraftService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// The PROMPT-module endpoint: ask the AI to turn a question into a query.
// The result is saved as a DRAFT widget and returned WITH the SQL so you can review it.
// (Until the real AI is wired in Step 15, this returns a clear "not implemented" message.)
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class PromptController {

    private final SqlDraftService sqlDraftService;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<GeneratedQueryResponse>> generate(
            @RequestBody @Valid GenerateQueryRequest request) {
        GeneratedQueryResponse draft = sqlDraftService.generateDraft(request.getQuestion(), request.getTitle());
        return ResponseEntity.ok(ApiResponse.success(draft,
                "Draft query generated. Review it, then approve to use it."));
    }
}
