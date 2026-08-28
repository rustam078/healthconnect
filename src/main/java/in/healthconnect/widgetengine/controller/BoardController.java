package in.healthconnect.widgetengine.controller;

import in.healthconnect.widgetengine.dto.request.CreateBoardRequest;
import in.healthconnect.widgetengine.dto.request.SaveBoardLayoutRequest;
import in.healthconnect.widgetengine.dto.response.BoardResponse;
import in.healthconnect.widgetengine.dto.response.BoardSummaryResponse;
import in.healthconnect.widgetengine.service.BoardService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Dashboard boards API.
//   POST   /api/v1/boards          create a board (name only)
//   GET    /api/v1/boards          list boards (for the switcher)
//   GET    /api/v1/boards/{id}     one board with its widgets
//   PUT    /api/v1/boards/{id}     save the board's layout (name + widgets)
//   DELETE /api/v1/boards/{id}     delete a board
@RestController
@RequestMapping("/api/v1/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @PostMapping
    public ResponseEntity<ApiResponse<BoardSummaryResponse>> create(
            @RequestBody @Valid CreateBoardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(boardService.create(request), "Board created"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BoardSummaryResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(boardService.list()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BoardResponse>> getOne(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(boardService.get(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BoardResponse>> save(
            @PathVariable Integer id,
            @RequestBody @Valid SaveBoardLayoutRequest request) {
        return ResponseEntity.ok(ApiResponse.success(boardService.save(id, request), "Board saved"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Integer id) {
        boardService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Board deleted"));
    }
}
