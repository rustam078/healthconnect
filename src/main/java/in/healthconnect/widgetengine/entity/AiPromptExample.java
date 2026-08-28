package in.healthconnect.widgetengine.entity;

import in.healthconnect.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

// One row here is an EXAMPLE for the AI: a plain-English question and the correct SQL for it.
// These "few-shot examples" are the cheapest, strongest way to make the AI's answers accurate.
// We send a few of them with each request so the AI copies the right style and joins.
@Entity
@Table(name = "ai_prompt_example")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE ai_prompt_example SET is_deleted = true WHERE id = ?")
public class AiPromptExample extends BaseEntity {

    // the plain-English question, e.g. "count all doctors"
    @Column(name = "question", nullable = false, length = 500)
    private String question;

    // the correct MySQL for that question
    @Column(name = "generated_sql", columnDefinition = "TEXT", nullable = false)
    private String generatedSql;

    // turn this example on/off without deleting it
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;
}
