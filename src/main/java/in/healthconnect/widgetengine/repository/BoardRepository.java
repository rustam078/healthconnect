package in.healthconnect.widgetengine.repository;

import in.healthconnect.widgetengine.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// Read/save dashboard boards.
@Repository
public interface BoardRepository extends JpaRepository<Board, Integer> {
}
