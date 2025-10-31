package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error.ErrorState;
import ch.admin.bit.jeap.errorhandling.web.api.ErrorGroupListSearchCriteria;
import ch.admin.bit.jeap.errorhandling.web.api.ErrorSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ErrorRepository extends JpaRepository<Error, UUID>, JpaSpecificationExecutor<Error> {

    default Page<Error> findByGroupIdAndCriteria(UUID errorGroupId, ErrorGroupListSearchCriteria criteria, Pageable pageable) {
        return findAll(ErrorGroupListSearchSpecification.fromCriteria(errorGroupId, criteria), pageable);
    }

    @Query("select e from Error e where (e.state = 'PERMANENT' OR e.state = 'SEND_TO_MANUALTASK') ORDER BY e.created DESC")
    Page<Error> findAllPermanent(Pageable pageable);

    Page<Error> findAllByStateEqualsOrderByCreatedDesc(ErrorState errorState, Pageable pageable);

    @Query("select count(e) from Error e where e.errorEventMetadata.idempotenceId = ?1")
    int countErrorsByErrorEventIdempotenceId(String errorEventIdempotenceId);

    @Query("select count(e) from Error e where e.causingEvent.metadata.id = ?1")
    int countErrorsForCausingEvent(String causingEventId);

    @Query("select count(e) from Error e where e.state = 'TEMPORARY_RETRY_PENDING'")
    int countErrorsInStateTemporaryRetryPending();

    @Query("select count(e) from Error e where e.state = 'PERMANENT' OR e.state = 'SEND_TO_MANUALTASK'")
    int countErrorsInStatesPermanentOrSendToManualTask();

    @Query("select count(e) from Error e where e.state = 'SEND_TO_MANUALTASK'")
    int countErrorsInStateSendToManualTask();

    @Query("select count(e) from Error e where e.state = 'RESOLVE_ON_MANUALTASK'")
    int countErrorsInStateResolveOnManualTask();

    @Query("select count(e) from Error e where e.state = 'DELETE_ON_MANUALTASK'")
    int countErrorsInStateDeleteOnManualTask();

    @Query("select new ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorCountByClusterNameResult(e.causingEvent.message.clusterName, count(e)) from Error e where e.state in ('PERMANENT', 'TEMPORARY_RETRY_PENDING', 'SEND_TO_MANUALTASK') group by e.causingEvent.message.clusterName")
    List<ErrorCountByClusterNameResult> countOpenErrorsByStateAndClusterName();

    Slice<ErrorQueryResult> findIdByStateInAndCreatedBefore(List<ErrorState> state, ZonedDateTime created, Pageable pageable);

    default Page<Error> search(ErrorSearchCriteria criteria, Pageable pageable) {
        return findAll(ErrorSearchSpecification.fromCriteria(criteria), pageable);
    }

    @Query("select distinct e.errorEventMetadata.publisher.service from Error e")
    List<String> getAllEventSources();

    @Query("select distinct e.errorEventData.code from Error e")
    List<String> getAllErrorCodes();

    @Query("select distinct e.causingEvent.metadata.type.name from Error e")
    List<String> getAllEventNames();

    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM error e WHERE e.id in (:errorIds) ")
    void deleteAllById(@Param("errorIds") Set<UUID> errorIds);
}
