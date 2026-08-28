package in.healthconnect.widgetengine.entity;

import in.healthconnect.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

// One row here describes ONE database table for the AI.
// This is the "knowledge base" - it tells Gemini what tables exist, what they mean,
// what columns they have, and any hints (like how tables join together).
// The AI reads these rows so it can write correct MySQL for a plain-English question.
@Entity
@Table(
        name = "ai_knowledge",
        uniqueConstraints = @UniqueConstraint(name = "uk_ai_knowledge_table", columnNames = "table_name")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE ai_knowledge SET is_deleted = true WHERE id = ?")
public class AiKnowledge extends BaseEntity {

    // the real table name in the database, e.g. "doctors"
    @Column(name = "table_name", nullable = false, unique = true, length = 150)
    private String tableName;

    // a short sentence: what is this table for? e.g. "Doctors in the hospital"
    @Column(name = "purpose", length = 500)
    private String purpose;

    // a compact list of columns, e.g. "id, first_name, last_name, email, status, is_deleted"
    // (compact on purpose - short text = fewer tokens sent to the AI)
    @Column(name = "columns_info", columnDefinition = "TEXT")
    private String columnsInfo;

    // extra hints for the AI, e.g. how this table joins to another, or what an enum means
    @Column(name = "hints", columnDefinition = "TEXT")
    private String hints;

    // turn this table's knowledge on/off without deleting it
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;
}
