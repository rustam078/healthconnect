package in.healthconnect.widgetengine.dto.response;

import in.healthconnect.widgetengine.engine.PreparedQuery;
import lombok.*;

import java.util.Map;

// Used by the "preview" endpoint. It shows the FINAL query and the values that would be
// bound to it - WITHOUT actually running it. This is handy for learning and debugging.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreviewResponse {

    private String sql;
    private Map<String, Object> params;

    public static PreviewResponse of(PreparedQuery prepared) {
        return PreviewResponse.builder()
                .sql(prepared.getSql())
                .params(prepared.getParams().getValues())
                .build();
    }
}
