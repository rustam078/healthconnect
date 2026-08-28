package in.healthconnect.widgetengine.dto.response;

import in.healthconnect.widgetengine.entity.Widget;
import in.healthconnect.widgetengine.entity.enums.WidgetModule;
import in.healthconnect.widgetengine.entity.enums.WidgetStatus;
import in.healthconnect.widgetengine.entity.enums.WidgetType;
import lombok.*;

// A short version of a widget, used for LISTS (e.g. the board suggestions).
// It leaves out the heavy fields and shows just enough to pick a widget.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WidgetSummaryResponse {

    private Integer id;
    private String code;
    private String name;
    private String description;
    private WidgetModule module;
    private WidgetType type;
    private Boolean enabled;

    // DRAFT or APPROVED. The widget gallery uses this to hide unapproved AI drafts.
    private WidgetStatus status;

    public static WidgetSummaryResponse from(Widget widget) {
        return WidgetSummaryResponse.builder()
                .id(widget.getId())
                .code(widget.getCode())
                .name(widget.getName())
                .description(widget.getDescription())
                .module(widget.getModule())
                .type(widget.getType())
                .status(widget.getStatus())
                .enabled(widget.getEnabled())
                .build();
    }
}
