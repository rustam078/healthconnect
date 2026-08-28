package in.healthconnect.widgetengine.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

// To save a board's layout: an optional new name, plus every widget on it with its
// position on the 3-column grid. The FE sends the WHOLE list each time - simplest to save.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SaveBoardLayoutRequest {

    private String name; // optional: rename the board

    // @Valid on the element is what makes the per-item rules below actually run.
    // Without it Spring only checks that the list is not null.
    @NotNull
    private List<@Valid Item> items;

    // One widget on the board: which column it starts in (x), which row (y),
    // how many columns it spans (w) and how many row units tall it is (h).
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @NotNull
        private Integer widgetId;

        @NotNull
        @Min(0)
        @Max(2)
        private Integer x;

        @NotNull
        @Min(0)
        private Integer y;

        @NotNull
        @Min(1)
        @Max(3)
        private Integer w;

        @NotNull
        @Min(1)
        private Integer h;

        // The grid is 3 columns wide, so a widget can never start at column 2 and span 2.
        // This spans two fields, so it cannot be a simple field annotation.
        // Returns true when either value is null - @NotNull already reports that case, and
        // reporting it twice would put two messages on one mistake.
        @AssertTrue(message = "x + w must not exceed 3 columns")
        public boolean isWithinGrid() {
            if (x == null || w == null) {
                return true;
            }
            return x + w <= 3;
        }
    }
}
