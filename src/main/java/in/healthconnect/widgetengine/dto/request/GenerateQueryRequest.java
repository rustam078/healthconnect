package in.healthconnect.widgetengine.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// What the UI sends to ask the AI for a query: the plain-English question, and
// optionally the title to show on the widget.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GenerateQueryRequest {

    @NotBlank
    @Size(max = 500)
    private String question;

    // Optional. What to show as the widget's heading on the board. When blank we fall
    // back to the question itself, which is what happened before this field existed.
    @Size(max = 200)
    private String title;
}
