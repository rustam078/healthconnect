package in.healthconnect.widgetengine.dto.response;

import in.healthconnect.widgetengine.entity.AiPromptExample;
import lombok.*;

// One AI example, sent back to the UI.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiPromptExampleResponse {

    private Integer id;
    private String question;
    private String generatedSql;
    private Boolean enabled;

    public static AiPromptExampleResponse from(AiPromptExample example) {
        return AiPromptExampleResponse.builder()
                .id(example.getId())
                .question(example.getQuestion())
                .generatedSql(example.getGeneratedSql())
                .enabled(example.getEnabled())
                .build();
    }
}
