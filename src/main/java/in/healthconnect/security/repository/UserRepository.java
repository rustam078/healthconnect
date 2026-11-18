package in.healthconnect.security.repository;

import in.healthconnect.security.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    // [patientId, avatarFileId] for the given patients that have a profile photo.
    @Query("select u.patientId, u.avatarFileId from User u where u.patientId in :patientIds and u.avatarFileId is not null")
    List<Object[]> avatarIdsByPatientIds(List<Integer> patientIds);
}
