package in.healthconnect.setting.repository;

import in.healthconnect.setting.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// Read/save application settings. The soft-delete filter comes free from BaseEntity.
@Repository
public interface AppSettingRepository extends JpaRepository<AppSetting, Integer> {

    // Look a setting up by its key, e.g. "nim.api-key".
    Optional<AppSetting> findByName(String name);

    boolean existsByName(String name);
}
