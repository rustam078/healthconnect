package in.healthconnect.widgetengine.dto.response;

import in.healthconnect.widgetengine.entity.Widget;
import in.healthconnect.widgetengine.entity.enums.WidgetStatus;
import lombok.*;

// The result of asking the AI to generate a query.
// Unlike WidgetResponse, this DOES include the SQL - because the whole point is to let
// you REVIEW the generated query before approving it. (This is an admin/review action.)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedQueryResponse {

    private Integer widgetId;
    private String code;
    private String question;
    private String name;      // the title shown on the widget (the title you gave, or the question)
    private WidgetStatus status;
    private String sql;

    public static GeneratedQueryResponse of(Widget widget) {
        return GeneratedQueryResponse.builder()
                .widgetId(widget.getId())
                .code(widget.getCode())
                .question(widget.getDescription())
                .name(widget.getName())
                .status(widget.getStatus())
                .sql(widget.getSqlTemplate())
                .build();
    }
}
