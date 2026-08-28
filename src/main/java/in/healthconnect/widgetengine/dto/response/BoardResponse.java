package in.healthconnect.widgetengine.dto.response;

import in.healthconnect.widgetengine.entity.enums.WidgetType;
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
        private Integer x;         // starting column, 0-2
        private Integer y;         // row, in grid row units
        private Integer w;         // column span, 1-3
        private Integer h;         // height, in grid row units
    }
}
