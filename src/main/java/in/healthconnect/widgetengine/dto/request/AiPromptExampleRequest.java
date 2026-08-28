package in.healthconnect.widgetengine.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// What the UI sends to add or update one AI example (a question + its correct SQL).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiPromptExampleRequest {

    @NotBlank
    @Size(max = 500)
    private String question;

    @NotBlank
    private String generatedSql;

    private Boolean enabled;
}
