package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CausingEventRepository extends JpaRepository<CausingEvent, UUID> {

    @Query("select c from CausingEvent c where c.metadata.id = ?1")
    Optional<CausingEvent> findByCausingEventId(String eventId);

    @Query("select causingEvent.id from CausingEvent causingEvent left join Error error on causingEvent.id = error.causingEvent.id where error is null")
    Slice<UUID> findCausingEventIdsWithoutError(Pageable pageable);

    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM causing_event ce WHERE ce.id in (:ids) ")
    void deleteAllById(@Param("ids") List<UUID> ids);

    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM message_header mh WHERE mh.causing_event_id in (:ids) ")
    void deleteAllHeadersByCausingEventIds(@Param("ids") List<UUID> ids);
}
