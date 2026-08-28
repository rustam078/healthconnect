package in.healthconnect.widgetengine.dto.request;

import tools.jackson.databind.JsonNode;
import in.healthconnect.widgetengine.entity.enums.WidgetModule;
import in.healthconnect.widgetengine.entity.enums.WidgetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// The data the client sends to CREATE a new widget.
// The @NotBlank / @NotNull / @Size checks run automatically before the controller code.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateWidgetRequest {

    // short unique name used to find the widget later, e.g. "active-patients-count"
    @NotBlank
    @Size(max = 150)
    private String code;

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotNull
    private WidgetModule module;

    @NotNull
    private WidgetType type;

    // the query with :name and {{name}} blanks
    @NotBlank
    private String sqlTemplate;

    // filter settings as real JSON (optional). Example:
    //   { "filters": [ { "key":"status", "operators":["eq","in"] } ],
    //     "sortableColumns": ["Patient"] }
    // We accept it as JSON here and store it as text in the database.
    private JsonNode filters;

    // optional; if not sent, the widget is turned on by default
    private Boolean enabled;
}
