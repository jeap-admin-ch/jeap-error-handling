package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupAggregatedData;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.jdbc.Sql;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@DataJpaTest
@Sql("/causing_event.sql")
@Sql("/error_group.sql")
@Sql("/error.sql")
@Import(PersistenceTestConfig.class)
class ErrorGroupRepositoryTest {

    @Autowired
    private ErrorGroupRepository errorGroupRepository;

    @Test
    void testExistByTicketNumber_whenTicketExists_returnTrue() {
        String ticketNumber = "TAPAS-144";
        boolean exists = errorGroupRepository.existsByTicketNumber(ticketNumber);
        Assertions.assertThat(exists).isTrue();
    }

    @Test
    void testExistByTicketNumber_whenTicketDoesNotExists_returnFalse() {
        String ticketNumber = "unknown";
        boolean exists = errorGroupRepository.existsByTicketNumber(ticketNumber);
        Assertions.assertThat(exists).isFalse();
    }

    @Test
    void testFindByErrorPublisherAndErrorCodeAndEventNameAndErrorStackTraceHash_WhenMatchesExist_returnOptionalWithErrorGroup() {
        String errorSource = "wvs-communication-service";
        String code = "MESSAGE_NOT_FOUND";
        String eventName = "MessageProcessingFailedEvent";
        String stackTraceHash = "stack-trace-hash-3";
        Optional<ErrorGroup> errorGroupOptional = errorGroupRepository.
                findByErrorPublisherAndErrorCodeAndEventNameAndErrorStackTraceHash(errorSource, code, eventName, stackTraceHash);
        Assertions.assertThat(errorGroupOptional).isNotEmpty();
    }

    @Test
    void testFindByErrorPublisherAndErrorCodeAndEventNameAndErrorStackTraceHash_WhenNoMatcheExists_returnEmptyOptional() {
        Optional<ErrorGroup> errorGroupOptional = errorGroupRepository.
                findByErrorPublisherAndErrorCodeAndEventNameAndErrorStackTraceHash("unknonw", "unknonw", "unknonw", "unknonw");
        Assertions.assertThat(errorGroupOptional).isEmpty();
    }

    @Test
    void testFindErrorGroupAggregatedData_returnsPageWithErrorGroupAggregatedData() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        // When
        Page<ErrorGroupAggregatedData> result = errorGroupRepository.findErrorGroupAggregatedData(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                pageable);
        // Then
        Assertions.assertThat(result).isNotNull().isNotEmpty();
        Assertions.assertThat(result.getContent().getLast().getGroupId()).isNotNull(); // UUID could be read
        Assertions.assertThat(result.getContent().getLast().getFirstErrorAt()).isNotNull(); // ZonedDateTime could be read
        Assertions.assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void testFindErrorGroupAggregatedData_returnsOptionalWithErrorGroupAggregatedData(){
        UUID groupId = UUID.fromString("cb7e65fc-1bb8-4192-af41-ba7bc6fdc395");

        Optional<ErrorGroupAggregatedData> errorGroupOptional = errorGroupRepository.findErrorGroupAggregatedData(groupId);

        Assertions.assertThat(errorGroupOptional).isNotEmpty();

        Assertions.assertThat(errorGroupOptional.get().getGroupId()).isNotNull(); // UUID could be read
        Assertions.assertThat(errorGroupOptional.get().getFirstErrorAt()).isNotNull(); // ZonedDateTime could be read

    }

    @Test
    void testFindErrorGroupAggregatedData_noTicketsIsTrue() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        // When
        Page<ErrorGroupAggregatedData> result = errorGroupRepository.findErrorGroupAggregatedData(
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                pageable);
        // Then
        Assertions.assertThat(result).isNotNull().isNotEmpty();
        Assertions.assertThat(result.getContent()).hasSize(1);
        Assertions.assertThat(result.getContent().getFirst().getTicketNumber()).isEmpty();

    }

    @Test
    void testFindErrorGroupAggregatedData_NoTicketsNotSet() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        // When
        Page<ErrorGroupAggregatedData> result = errorGroupRepository.findErrorGroupAggregatedData(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                pageable);
        // Then
        Assertions.assertThat(result).isNotNull().isNotEmpty();
        Assertions.assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void testFindErrorGroupAggregatedData_DateInRange() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<ErrorGroupAggregatedData> result = errorGroupRepository.findErrorGroupAggregatedData(
                false,
                ZonedDateTime.parse("2023-01-01T00:00:00Z"),
                ZonedDateTime.parse("2027-01-01T00:00:00Z"),
                null,
                null,
                null,
                null,
                pageable);
        // Then
        Assertions.assertThat(result).isNotNull().isNotEmpty();
        Assertions.assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void testFindErrorGroupAggregatedData_DateOutOfRange() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        // When
        Page<ErrorGroupAggregatedData> result = errorGroupRepository.findErrorGroupAggregatedData(
                false,
                ZonedDateTime.parse("2000-01-01T00:00:00Z"),
                ZonedDateTime.parse("2001-01-01T00:00:00Z"),
                null,
                null,
                null,
                null,
                pageable);
        // Then
        Assertions.assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void testFindErrorGroupAggregatedData_withSource() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        // When
        Page<ErrorGroupAggregatedData> result = errorGroupRepository.findErrorGroupAggregatedData(
                false,
                null,
                null,
                "wvs-communication-service",
                null,
                null,
                null,
                pageable);
        // Then
        Assertions.assertThat(result).isNotNull().isNotEmpty();
        Assertions.assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void testFindErrorGroupAggregatedData_notExistingSource() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        // When
        Page<ErrorGroupAggregatedData> result = errorGroupRepository.findErrorGroupAggregatedData(
                false,
                null,
                null,
                "not-existing-source",
                null,
                null,
                null,
                pageable);
        // Then
        Assertions.assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void testFindErrorGroupAggregatedData_EventName() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        // When
        Page<ErrorGroupAggregatedData> result = errorGroupRepository.findErrorGroupAggregatedData(
                false,
                null,
                null,
                null,
                "MessageProcessingFailedEvent",
                null,
                null,
                pageable);
        // Then
        Assertions.assertThat(result).isNotNull().isNotEmpty();
        Assertions.assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void countErrorGroupsWithErrorsInStates_whenStateMatches_returnsNumberOfGroups() {
        int count = errorGroupRepository.countErrorGroupsWithErrorsInStates(Set.of(Error.ErrorState.PERMANENT));

        Assertions.assertThat(count).isEqualTo(2);
    }

    @Test
    void countErrorGroupsWithErrorsInStates_whenStateDoesNotMatch_returnsZero() {
        int count = errorGroupRepository.countErrorGroupsWithErrorsInStates(Set.of(Error.ErrorState.SEND_TO_MANUALTASK));

        Assertions.assertThat(count).isZero();
    }

}
