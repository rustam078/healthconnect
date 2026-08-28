package in.healthconnect.widgetengine.service;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.widgetengine.dto.request.CreateBoardRequest;
import in.healthconnect.widgetengine.dto.request.SaveBoardLayoutRequest;
import in.healthconnect.widgetengine.dto.response.BoardResponse;
import in.healthconnect.widgetengine.entity.Board;
import in.healthconnect.widgetengine.entity.Widget;
import in.healthconnect.widgetengine.entity.enums.WidgetType;
import in.healthconnect.widgetengine.repository.BoardRepository;
import in.healthconnect.widgetengine.repository.WidgetRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Tests for BoardService. Real Jackson mapper; mock repositories (no database).
//
// The important behaviour here is NORMALIZATION. Boards saved before the grid existed look
// like [{"widgetId":12,"width":1}] - no x/y/w/h. We fill those in on READ, laying them out
// the way the old UI did: left to right, 3 columns per row, in stored order. That means an
// existing board opens unchanged and the first save rewrites it in the new shape.
class BoardServiceTest {

    private final BoardRepository boardRepository = mock(BoardRepository.class);
    private final WidgetRepository widgetRepository = mock(WidgetRepository.class);
    private final BoardService service =
            new BoardService(boardRepository, widgetRepository, JsonMapper.builder().build());

    private Widget widget(int id, String code, String name, WidgetType type) {
        Widget w = new Widget();
        w.setId(id);
        w.setCode(code);
        w.setName(name);
        w.setType(type);
        return w;
    }

    // Register a board with the given raw layout JSON, and widgets 1..5 so lookups succeed.
    private Board boardWithLayout(String layoutJson) {
        Board board = new Board();
        board.setId(1);
        board.setName("Ops");
        board.setLayout(layoutJson);
        when(boardRepository.findById(1)).thenReturn(Optional.of(board));
        for (int id = 1; id <= 5; id++) {
            when(widgetRepository.findById(id))
                    .thenReturn(Optional.of(widget(id, "w" + id, "Widget " + id, WidgetType.TABLE)));
        }
        return board;
    }

    @Test
    void createStoresNameAndEmptyLayout() {
        when(boardRepository.save(any(Board.class))).thenAnswer(call -> call.getArgument(0));
        CreateBoardRequest request = new CreateBoardRequest();
        request.setName("My Board");

        service.create(request);

        ArgumentCaptor<Board> captor = ArgumentCaptor.forClass(Board.class);
        verify(boardRepository).save(captor.capture());
        assertEquals("My Board", captor.getValue().getName());
        assertEquals("[]", captor.getValue().getLayout()); // starts empty
    }

    @Test
    void getThrowsWhenBoardMissing() {
        when(boardRepository.findById(99)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(99));
    }

    @Test
    void legacyItemsAreFlowPackedIntoThreeColumnRows() {
        boardWithLayout("[{\"widgetId\":1,\"width\":1},{\"widgetId\":2,\"width\":2}]");

        List<BoardResponse.Item> items = service.get(1).getItems();

        assertEquals(2, items.size());
        assertEquals(0, items.get(0).getX());
        assertEquals(0, items.get(0).getY());
        assertEquals(1, items.get(0).getW());
        assertEquals(BoardService.DEFAULT_HEIGHT, items.get(0).getH());

        assertEquals(1, items.get(1).getX()); // sits beside the first
        assertEquals(0, items.get(1).getY());
        assertEquals(2, items.get(1).getW());
    }

    @Test
    void aLegacyItemThatDoesNotFitStartsTheNextRow() {
        // 1 + 3 > 3, so the full-width item must wrap
        boardWithLayout("[{\"widgetId\":1,\"width\":1},{\"widgetId\":2,\"width\":3}]");

        List<BoardResponse.Item> items = service.get(1).getItems();

        assertEquals(0, items.get(0).getX());
        assertEquals(0, items.get(0).getY());

        assertEquals(0, items.get(1).getX());
        assertEquals(BoardService.DEFAULT_HEIGHT, items.get(1).getY()); // next row down
        assertEquals(3, items.get(1).getW());
    }

    @Test
    void legacyItemWithNoWidthDefaultsToOneColumn() {
        boardWithLayout("[{\"widgetId\":1}]");

        BoardResponse.Item item = service.get(1).getItems().get(0);

        assertEquals(1, item.getW());
        assertEquals(0, item.getX());
        assertEquals(0, item.getY());
    }

    @Test
    void itemsThatAlreadyHaveCoordinatesAreLeftAlone() {
        boardWithLayout("[{\"widgetId\":1,\"x\":2,\"y\":7,\"w\":1,\"h\":9}]");

        BoardResponse.Item item = service.get(1).getItems().get(0);

        assertEquals(2, item.getX());
        assertEquals(7, item.getY());
        assertEquals(1, item.getW());
        assertEquals(9, item.getH());
    }

    @Test
    void aMixedLayoutOnlyNormalizesTheItemsThatNeedIt() {
        boardWithLayout("[{\"widgetId\":1,\"x\":0,\"y\":0,\"w\":3,\"h\":5},{\"widgetId\":2,\"width\":2}]");

        List<BoardResponse.Item> items = service.get(1).getItems();

        assertEquals(3, items.get(0).getW());   // untouched
        assertEquals(5, items.get(0).getH());
        assertEquals(2, items.get(1).getW());   // normalized
        assertEquals(BoardService.DEFAULT_HEIGHT, items.get(1).getH());
    }

    @Test
    void aBadLayoutGivesAnEmptyBoardRatherThanAnError() {
        boardWithLayout("this is not json");

        assertTrue(service.get(1).getItems().isEmpty());
    }

    @Test
    void aWidgetThatNoLongerExistsIsSkipped() {
        Board board = new Board();
        board.setId(1);
        board.setName("Ops");
        board.setLayout("[{\"widgetId\":404,\"x\":0,\"y\":0,\"w\":1,\"h\":4}]");
        when(boardRepository.findById(1)).thenReturn(Optional.of(board));
        when(widgetRepository.findById(404)).thenReturn(Optional.empty());

        assertTrue(service.get(1).getItems().isEmpty());
    }

    @Test
    void saveWritesGridCoordinatesAndNeverWritesTheLegacyWidthField() {
        Board board = boardWithLayout("[]");
        when(boardRepository.save(any(Board.class))).thenAnswer(call -> call.getArgument(0));

        SaveBoardLayoutRequest request = new SaveBoardLayoutRequest();
        request.setItems(List.of(new SaveBoardLayoutRequest.Item(1, 2, 4, 1, 6)));

        BoardResponse response = service.save(1, request);

        assertTrue(board.getLayout().contains("\"widgetId\":1"));
        assertTrue(board.getLayout().contains("\"x\":2"));
        assertTrue(board.getLayout().contains("\"y\":4"));
        assertTrue(board.getLayout().contains("\"w\":1"));
        assertTrue(board.getLayout().contains("\"h\":6"));
        assertFalse(board.getLayout().contains("width"), "legacy width must never be written");

        BoardResponse.Item item = response.getItems().get(0);
        assertEquals(2, item.getX());
        assertEquals(4, item.getY());
        assertEquals(1, item.getW());
        assertEquals(6, item.getH());
    }

    @Test
    void saveRenamesTheBoardWhenANameIsGiven() {
        Board board = boardWithLayout("[]");
        when(boardRepository.save(any(Board.class))).thenAnswer(call -> call.getArgument(0));

        SaveBoardLayoutRequest request = new SaveBoardLayoutRequest();
        request.setName("Renamed");
        request.setItems(List.of());

        service.save(1, request);

        assertEquals("Renamed", board.getName());
    }
}
