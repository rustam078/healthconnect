package in.healthconnect.widgetengine.dto.response;

import in.healthconnect.widgetengine.entity.Board;
import lombok.*;

// A board in the list (just enough to show in the board switcher).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardSummaryResponse {

    private Integer id;
    private String name;

    public static BoardSummaryResponse from(Board board) {
        return BoardSummaryResponse.builder()
                .id(board.getId())
                .name(board.getName())
                .build();
    }
}
