package in.healthconnect.storage.repository;

import in.healthconnect.storage.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, Integer> {
}
