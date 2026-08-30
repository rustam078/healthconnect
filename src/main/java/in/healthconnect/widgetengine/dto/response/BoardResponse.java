package in.healthconnect.widgetengine.dto.response;

import in.healthconnect.widgetengine.entity.enums.WidgetType;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.*;

import java.util.List;

// A full board: its name + the widgets on it (in order).
// Each item carries enough widget info for the UI to draw a card and fetch its data.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardResponse {

    private Integer id;
    private String name;
    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private Integer widgetId;
        private String code;       // used by the UI to call /widgets/{code}/data
        private String name;       // widget title to show on the card
        private WidgetType type;   // how to draw it (COUNT/TABLE/BAR/LINE/PIE)

        // The widget's filter settings, passed straight through as JSON so a card can draw
        // its own filter controls. Sent here rather than fetched per card: a board of ten
        // widgets would otherwise cost ten extra requests to learn something the board
        // already had in hand.
        
        private String filters;
        private Integer x;         // starting column, 0-2
        private Integer y;         // row, in grid row units
        private Integer w;         // column span, 1-3
        private Integer h;         // height, in grid row units
    }
}
