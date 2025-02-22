package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupAggregatedData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ErrorGroupRepository extends JpaRepository<ErrorGroup, UUID> {

    String ERROR_GROUP_AGGREGATED_DATA_SELECTOR = """
                SELECT eg.id as groupId, count(e.id) as errorCount,
                    eg.eventName as errorEvent, eg.errorPublisher as errorPublisher,
                    eg.errorCode as errorCode, eg.errorMessage as errorMessage,
                    min(e.created) as firstErrorAt,
                    max(e.created) as latestErrorAt,
                    eg.ticketNumber as ticketNumber, eg.freeText as freeText
                FROM Error e
                JOIN e.errorGroup eg
                WHERE e.state IN ('SEND_TO_MANUALTASK', 'PERMANENT')
            """;
    String ERROR_GROUP_AGGREGATED_DATA_GROUPING =
            " GROUP BY eg.id, eg.eventName, eg.errorPublisher, eg.errorCode, eg.errorMessage, eg.freeText, eg.ticketNumber ";

    @Query(value = ERROR_GROUP_AGGREGATED_DATA_SELECTOR + ERROR_GROUP_AGGREGATED_DATA_GROUPING + """
                order by errorCount desc
                offset :#{#pageable.offset} rows
                fetch next :#{#pageable.pageSize} rows only""",
            countQuery = "select count(*) from ErrorGroup")
    Page<ErrorGroupAggregatedData> findErrorGroupAggregatedData(Pageable pageable);

    @Query(ERROR_GROUP_AGGREGATED_DATA_SELECTOR + " AND eg.id = :id " + ERROR_GROUP_AGGREGATED_DATA_GROUPING)
    Optional<ErrorGroupAggregatedData> findErrorGroupAggregatedData(@Param("id") UUID errorGroupId);

    Optional<ErrorGroup> findByErrorPublisherAndErrorCodeAndEventNameAndErrorStackTraceHash(
            String publisher, String code, String eventName, String stackTraceHash);

    @Query("select errorGroup.id from ErrorGroup errorGroup left join Error error on errorGroup.id = error.errorGroup.id where error is null")
    Slice<UUID> findUnreferencedErrorGroups(Pageable pageable);

    boolean existsByTicketNumber(String ticketNumber);
}
