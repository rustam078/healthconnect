package in.healthconnect.widgetengine.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// What the client sends to RUN a query that is NOT saved as a widget.
//
// There is no code, name or type here on purpose: this asks one question only -
// "does this query run, and what does it return?" Everything else about the widget
// is decided after the answer.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DryRunWidgetRequest {

    // the query, with the same :name and {{name}} blanks a saved widget may use
    @NotBlank
    private String sqlTemplate;

    // optional; the engine applies its own default (50) and cap (200) when null
    private Integer pageSize;
}
