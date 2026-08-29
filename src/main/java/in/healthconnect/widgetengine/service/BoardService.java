package in.healthconnect.widgetengine.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.widgetengine.dto.request.CreateBoardRequest;
import in.healthconnect.widgetengine.dto.request.SaveBoardLayoutRequest;
import in.healthconnect.widgetengine.dto.response.BoardResponse;
import in.healthconnect.widgetengine.dto.response.BoardSummaryResponse;
import in.healthconnect.widgetengine.entity.Board;
import in.healthconnect.widgetengine.entity.Widget;
import in.healthconnect.widgetengine.repository.BoardRepository;
import in.healthconnect.widgetengine.repository.WidgetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Manages dashboard boards: create (name only), list, get (with widgets resolved),
// save layout (which widgets, where on the 3-column grid, and how big), delete.
// The layout is stored as JSON text; here we read/write it, fill in coordinates for
// boards saved before the grid existed, and look up each widget's info.
@Service
public class BoardService {

    // The board grid is 3 columns wide.
    static final int COLS = 3;

    // Height, in grid row units, given to a widget that has never had one
    // (with the frontend's rowHeight of 80px this is about the height of the old cards).
    static final int DEFAULT_HEIGHT = 4;

    private final BoardRepository boardRepository;
    private final WidgetRepository widgetRepository;
    private final ObjectMapper objectMapper;

    public BoardService(BoardRepository boardRepository, WidgetRepository widgetRepository, ObjectMapper objectMapper) {
        this.boardRepository = boardRepository;
        this.widgetRepository = widgetRepository;
        this.objectMapper = objectMapper;
    }

    public BoardSummaryResponse create(CreateBoardRequest request) {
        Board board = new Board();
        board.setName(request.getName());
        board.setLayout("[]"); // starts with no widgets
        return BoardSummaryResponse.from(boardRepository.save(board));
    }

    @Transactional(readOnly = true)
    public List<BoardSummaryResponse> list() {
        return boardRepository.findAll().stream().map(BoardSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BoardResponse get(Integer id) {
        Board board = findBoard(id);
        return toResponse(board);
    }

    public BoardResponse save(Integer id, SaveBoardLayoutRequest request) {
        Board board = findBoard(id);
        if (request.getName() != null && !request.getName().isBlank()) {
            board.setName(request.getName());
        }
        // turn the incoming items into our small storage shape and save as JSON text
        List<LayoutItem> items = new ArrayList<>();
        if (request.getItems() != null) {
            for (SaveBoardLayoutRequest.Item item : request.getItems()) {
                // width is null: we never write the legacy field
                items.add(new LayoutItem(item.getWidgetId(),
                        item.getX(), item.getY(), item.getW(), item.getH(), null));
            }
        }
        board.setLayout(writeJson(items));
        return toResponse(boardRepository.save(board));
    }

    public void delete(Integer id) {
        boardRepository.delete(findBoard(id)); // soft delete
    }

    // ---- helpers ----

    private Board findBoard(Integer id) {
        return boardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Board not found: " + id));
    }

    // Build the full response: for each stored item, look up the widget's code/name/type.
    private BoardResponse toResponse(Board board) {
        List<BoardResponse.Item> items = new ArrayList<>();
        for (LayoutItem stored : normalize(readItems(board.getLayout()))) {
            Optional<Widget> widget = widgetRepository.findById(stored.widgetId());
            if (widget.isEmpty()) {
                continue; // widget was deleted - skip it
            }
            Widget w = widget.get();
            items.add(BoardResponse.Item.builder()
                    .widgetId(w.getId())
                    .code(w.getCode())
                    .name(w.getName())
                    .type(w.getType())
                    .filters(w.getFilters())
                    .x(stored.x())
                    .y(stored.y())
                    .w(stored.w())
                    .h(stored.h())
                    .build());
        }
        return BoardResponse.builder()
                .id(board.getId())
                .name(board.getName())
                .items(items)
                .build();
    }


    // Fill in grid coordinates for items saved before the grid existed.
    // Old items have only `width`, so we lay them out the way the old UI did: flow them
    // left to right, wrapping after 3 columns, in the order they were stored.
    // Items that already carry coordinates are passed through untouched.
    private List<LayoutItem> normalize(List<LayoutItem> stored) {
        List<LayoutItem> out = new ArrayList<>(stored.size());
        int cursorX = 0;
        int cursorY = 0;

        for (LayoutItem item : stored) {
            boolean positioned = item.x() != null && item.y() != null
                    && item.w() != null && item.h() != null;
            if (positioned) {
                out.add(item);
                continue;
            }

            int w = clampWidth(item.width());
            if (cursorX + w > COLS) {   // does not fit on this row - start the next one
                cursorX = 0;
                cursorY += DEFAULT_HEIGHT;
            }
            out.add(new LayoutItem(item.widgetId(), cursorX, cursorY, w, DEFAULT_HEIGHT, null));
            cursorX += w;
        }
        return out;
    }

    // A legacy width of null, 0 or 47 should still produce a usable column span.
    private int clampWidth(Integer width) {
        if (width == null) {
            return 1;
        }
        return Math.max(1, Math.min(COLS, width));
    }
    private List<LayoutItem> readItems(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(objectMapper.readValue(json, LayoutItem[].class));
        } catch (Exception e) {
            return List.of(); // a bad layout should not break the whole board
        }
    }

    private String writeJson(List<LayoutItem> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
        }
    }

    // What we store per widget on a board.
    //
    // x/y is the position on a 3-column grid, w/h the span and height in row units.
    // `width` is LEGACY: boards saved before the grid existed look like
    // [{"widgetId":12,"width":1}]. We only ever READ it - normalize() turns it into
    // coordinates - and @JsonInclude(NON_NULL) keeps it out of everything we write.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LayoutItem(
            Integer widgetId,
            Integer x, Integer y,
            Integer w, Integer h,
            Integer width
    ) {
    }
}
