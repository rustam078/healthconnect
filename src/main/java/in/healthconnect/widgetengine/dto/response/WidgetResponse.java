package in.healthconnect.widgetengine.dto.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import in.healthconnect.widgetengine.entity.Widget;
import in.healthconnect.widgetengine.entity.enums.WidgetModule;
import in.healthconnect.widgetengine.entity.enums.WidgetStatus;
import in.healthconnect.widgetengine.entity.enums.WidgetType;
import lombok.*;

import java.time.Instant;

// The full details of one widget that we send back to the client.
// The sqlTemplate IS here. It was left out while dashboards were the only caller, but the
// developer tab writes and edits queries by hand, and it cannot pre-fill a form with a
// query it is never told. Same audience that already types the SQL - nothing new is
// revealed. When auth lands this endpoint takes the same role check as that tab.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WidgetResponse {

    private Integer id;
    private String code;
    private String name;
    private String description;
    private WidgetModule module;
    private WidgetType type;

    // the query itself, with its :name and {{name}} blanks intact
    private String sqlTemplate;

    // send the stored filter settings back as real JSON (not as a quoted string)
    @JsonRawValue
    private String filters;

    private Boolean enabled;
    private WidgetStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    // Turn a Widget (database object) into this response object.
    public static WidgetResponse from(Widget widget) {
        return WidgetResponse.builder()
                .id(widget.getId())
                .code(widget.getCode())
                .name(widget.getName())
                .description(widget.getDescription())
                .module(widget.getModule())
                .type(widget.getType())
                .sqlTemplate(widget.getSqlTemplate())
                .filters(widget.getFilters())
                .enabled(widget.getEnabled())
                .status(widget.getStatus())
                .createdAt(widget.getCreatedAt())
                .updatedAt(widget.getUpdatedAt())
                .build();
    }
}
