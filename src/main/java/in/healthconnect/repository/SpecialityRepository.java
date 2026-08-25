package in.healthconnect.repository;

import in.healthconnect.entity.Specialty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;


public interface SpecialityRepository extends JpaRepository<Specialty, Integer > {

    @Query("""
            SELECT s
            FROM Specialty s
            WHERE (:specialityId IS NULL OR s.id = :specialityId)
              AND (
                    :search IS NULL
                    OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%') )
                    or Lower(s.description) Like Lower(CONCAT('%', :search, '%') ) 
                  )
            """)
    Page<Specialty> searchSpecialties(
            @Param("specialityId") Integer specialityId, @Param("search") String search, Pageable pageable);


    boolean existsByNameIgnoreCase(String name);

    List<Specialty> findAllByIdInAndDeletedFalse(Set<Integer> specialtyIds);
}
