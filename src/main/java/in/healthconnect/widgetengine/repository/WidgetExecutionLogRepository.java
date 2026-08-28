package in.healthconnect.widgetengine.repository;

import in.healthconnect.widgetengine.entity.WidgetExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// This is how we save history records in the database.
// We only ever add new rows here, so the free "save" method from
// JpaRepository is all we really need.
@Repository
public interface WidgetExecutionLogRepository extends JpaRepository<WidgetExecutionLog, Integer> {
}
