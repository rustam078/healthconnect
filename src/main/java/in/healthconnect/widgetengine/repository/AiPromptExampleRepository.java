package in.healthconnect.widgetengine.repository;

import in.healthconnect.widgetengine.entity.AiPromptExample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// Read/save the AI example rows (question + correct SQL).
@Repository
public interface AiPromptExampleRepository extends JpaRepository<AiPromptExample, Integer> {

    // all switched-on examples (we send a few of these to the AI to guide it)
    List<AiPromptExample> findByEnabledTrue();
}
