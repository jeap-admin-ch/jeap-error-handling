package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ScheduledResendRepository extends JpaRepository<ScheduledResend, UUID> {

    @Query("select sr from ScheduledResend sr where sr.resentAt is null and not sr.cancelled = TRUE and sr.resendAt <= ?1 order by sr.resendAt")
    List<ScheduledResend> findNextScheduledResendsOldestFirst(ZonedDateTime notAfter, Pageable pageable);

    default List<ScheduledResend> findNextScheduledResendsOldestFirst(ZonedDateTime notAfter, int maxNumItemsToFind) {
        return findNextScheduledResendsOldestFirst(notAfter, PageRequest.of(0, maxNumItemsToFind));
    }

    List<ScheduledResend> findByErrorId(UUID errorId);

    Optional<ScheduledResend> findFirstByErrorIdAndCancelledIsFalseAndResentAtIsNullOrderByResendAtAsc(UUID errorId);

    default Optional<ScheduledResend> findNextScheduledResend(UUID errorId) {
        return findFirstByErrorIdAndCancelledIsFalseAndResentAtIsNullOrderByResendAtAsc(errorId);
    }

    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM scheduled_resend sr WHERE sr.error_id in (:errorIds) ")
    void deleteAllByErrorIdIn(@Param("errorIds") Set<UUID> errorIds);

}
