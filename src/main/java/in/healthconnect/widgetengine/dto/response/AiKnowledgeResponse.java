package in.healthconnect.widgetengine.dto.response;

import in.healthconnect.widgetengine.entity.AiKnowledge;
import lombok.*;

// One table's knowledge, sent back to the UI.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiKnowledgeResponse {

    private Integer id;
    private String tableName;
    private String purpose;
    private String columnsInfo;
    private String hints;
    private Boolean enabled;

    public static AiKnowledgeResponse from(AiKnowledge knowledge) {
        return AiKnowledgeResponse.builder()
                .id(knowledge.getId())
                .tableName(knowledge.getTableName())
                .purpose(knowledge.getPurpose())
                .columnsInfo(knowledge.getColumnsInfo())
                .hints(knowledge.getHints())
                .enabled(knowledge.getEnabled())
                .build();
    }
}
