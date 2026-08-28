package in.healthconnect.widgetengine.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// What the UI sends to add or update one table's knowledge for the AI.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeRequest {

    @NotBlank
    @Size(max = 150)
    private String tableName;

    @Size(max = 500)
    private String purpose;

    // compact comma list of columns, e.g. "id, first_name, last_name, status, is_deleted"
    private String columnsInfo;

    // hints for the AI: joins, enum meanings, etc.
    private String hints;

    // optional; defaults to on when creating
    private Boolean enabled;
}
