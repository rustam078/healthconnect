package in.healthconnect.widgetengine.repository;

import in.healthconnect.widgetengine.entity.AiKnowledge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Read/save the AI knowledge-base rows (one per table).
@Repository
public interface AiKnowledgeRepository extends JpaRepository<AiKnowledge, Integer> {

    // all switched-on knowledge rows, in table-name order (this is what we send to the AI)
    List<AiKnowledge> findByEnabledTrueOrderByTableNameAsc();

    // find the knowledge for one table (used when adding/updating)
    Optional<AiKnowledge> findByTableName(String tableName);

    boolean existsByTableName(String tableName);
}
