package in.healthconnect.widgetengine.repository;

import in.healthconnect.widgetengine.entity.Widget;
import in.healthconnect.widgetengine.entity.enums.WidgetModule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// This is how we read/save widgets in the database.
// By extending JpaRepository we already get common methods for free:
//   save, findById, findAll, deleteById ... (we don't have to write them).
// Below we just add a few extra finders. Spring builds the SQL for us
// automatically from the method names.
@Repository
public interface WidgetRepository extends JpaRepository<Widget, Integer> {

    // Find one widget by its code (e.g. "active-patients-count").
    // Returns "empty" if none is found.
    Optional<Widget> findByCode(String code);

    // Quick yes/no check: does a widget with this code already exist?
    // We use this before creating one, so codes stay unique.
    boolean existsByCode(String code);

    // Get widgets in one group (e.g. all WIDGET rows), one page at a time.
    // "Pageable" means the caller asks for page 0, page 1, etc. instead of everything at once.
    Page<Widget> findByModule(WidgetModule module, Pageable pageable);

    // Does ANY row hold this code - including ones that were soft-deleted?
    //
    // Widget carries @SQLRestriction("is_deleted = false"), so existsByCode above cannot
    // see deleted rows. The database's uk_widget_code unique key CAN: it is on the raw
    // column. Generating a question whose widget was deleted earlier therefore passed the
    // exists check and then failed the INSERT with a duplicate-key error.
    // A native query bypasses the restriction, so we compare against what MySQL enforces.
    // COUNT(*) returning a long, not "COUNT(*) > 0" returning a boolean: the boolean form
    // depends on the driver mapping BIGINT 0/1 onto Boolean, which is not dependable.
    @Query(value = "SELECT COUNT(*) FROM widget WHERE code = :code", nativeQuery = true)
    long countByCodeIncludingDeleted(@Param("code") String code);

    // Really remove the row, rather than marking is_deleted = true.
    //
    // Widget carries @SQLDelete, so the ordinary delete() can only ever soft-delete. A
    // soft-deleted row keeps its code in the uk_widget_code unique index forever, which is
    // why asking the same question twice used to produce "-2", "-3" suffixes. For a DRAFT
    // that was never approved there is nothing worth keeping, so we free the code.
    @Modifying
    @Query(value = "DELETE FROM widget WHERE id = :id", nativeQuery = true)
    void hardDeleteById(@Param("id") Integer id);
}
