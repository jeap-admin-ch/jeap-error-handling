package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    default List<AuditLog> findAllByErrorId(UUID errorId) {
        return findAllByErrorIdOrderByCreatedDesc(errorId);
    }

    List<AuditLog> findAllByErrorIdOrderByCreatedDesc(UUID errorId);

    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM audit_log al WHERE al.error_id in (:errorIds) ")
    void deleteAllByErrorIdIn(@Param("errorIds") Set<UUID> errorIds);

}
