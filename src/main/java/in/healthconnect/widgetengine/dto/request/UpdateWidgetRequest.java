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

// The data the client sends to UPDATE an existing widget.
// Note: "code" is NOT here on purpose - the code should stay the same for the life of
// the widget, because other places (like integration URLs) rely on it.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWidgetRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotNull
    private WidgetModule module;

    @NotNull
    private WidgetType type;

    @NotBlank
    private String sqlTemplate;

    private JsonNode filters;

    private Boolean enabled;
}
