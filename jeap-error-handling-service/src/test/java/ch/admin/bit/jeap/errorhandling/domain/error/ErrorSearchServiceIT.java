package ch.admin.bit.jeap.errorhandling.domain.error;

import ch.admin.bit.jeap.errorhandling.ErrorHandlingITBase;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.*;
import ch.admin.bit.jeap.errorhandling.web.api.ErrorSearchCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ErrorSearchServiceIT extends ErrorHandlingITBase {

    @Autowired
    private ErrorSearchService service;
    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearRepository() {
        errorRepository.deleteAll();
    }

    @Test
    void search_withParams_sortCreatedDescAsc() {

        final Error error1 = saveError(Error.ErrorState.PERMANENT, "service", "eventName", ZonedDateTime.now().minusDays(3), "123", "myTraceId");
        final Error error2 = saveError(Error.ErrorState.PERMANENT, "service", "eventName", ZonedDateTime.now().minusDays(1), "123", "myTraceId");
        final Error error3 = saveError(Error.ErrorState.PERMANENT, "service", "eventName", ZonedDateTime.now().minusDays(2), "123", "myTraceId");
        storeErrors();

        ErrorSearchCriteria criteria = ErrorSearchCriteria.builder()
                .states(List.of(Error.ErrorState.PERMANENT))
                .errorCode("123")
                .eventName("eventName")
                .serviceName("service")
                .traceId("myTraceId")
                .sort(new String[]{"created,desc"}).build();
        ErrorList errorList = service.search(criteria);

        assertThat(errorList.getTotalElements()).isEqualTo(3);
        assertThat(errorList.getErrors()).hasSize(3);
        assertThat(errorList.getErrors().get(0).getId()).isEqualTo(error2.getId());
        assertThat(errorList.getErrors().get(1).getId()).isEqualTo(error3.getId());
        assertThat(errorList.getErrors().get(2).getId()).isEqualTo(error1.getId());

        criteria = ErrorSearchCriteria.builder()
                .states(List.of(Error.ErrorState.PERMANENT))
                .errorCode("123")
                .eventName("eventName")
                .serviceName("service")
                .traceId("myTraceId")
                .sort(new String[]{"created,asc"}).build();
        errorList = service.search(criteria);
        assertThat(errorList.getTotalElements()).isEqualTo(3);
        assertThat(errorList.getErrors()).hasSize(3);
        assertThat(errorList.getErrors().get(0).getId()).isEqualTo(error1.getId());
        assertThat(errorList.getErrors().get(1).getId()).isEqualTo(error3.getId());
        assertThat(errorList.getErrors().get(2).getId()).isEqualTo(error2.getId());

    }

    @Test
    void search_withoutParams_allErrorsFound() {
        //given
        storeErrors();

        //when
        final ErrorSearchCriteria criteria = ErrorSearchCriteria.builder().sort(new String[]{"created,desc"}).build();
        final ErrorList result = service.search(criteria);

        //then
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getErrors()).hasSize(5);

    }

    @Test
    void search_sortByEventNameDesc_errorsSortedByNameDesc() {
        //given
        storeErrors();

        //when
        final ErrorSearchCriteria criteria = ErrorSearchCriteria.builder()
                .sort(new String[]{"errorEventMetadata.type.name,desc"}).build();
        final ErrorList result = service.search(criteria);

        //then
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getErrors()).hasSize(5);
        assertThat(result.getErrors().get(0).getCausingEventMetadata().getType().getName()).isEqualTo("eventName2");
        assertThat(result.getErrors().get(1).getCausingEventMetadata().getType().getName()).isEqualTo("eventName");
        assertThat(result.getErrors().get(2).getCausingEventMetadata().getType().getName()).isEqualTo("eventName");
    }

    @Test
    void search_sortByEventNameAsc_errorsSortedByNameAsc() {
        //given
        storeErrors();

        //when
        final ErrorSearchCriteria criteria = ErrorSearchCriteria.builder().sort(new String[]{"errorEventMetadata.type.name,asc"}).build();
        final ErrorList result = service.search(criteria);

        //then
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getErrors()).hasSize(5);
        assertThat(result.getErrors().get(0).getCausingEventMetadata().getType().getName()).isEqualTo("eventName");
        assertThat(result.getErrors().get(1).getCausingEventMetadata().getType().getName()).isEqualTo("eventName");
        assertThat(result.getErrors().get(2).getCausingEventMetadata().getType().getName()).isEqualTo("eventName");
        assertThat(result.getErrors().get(3).getCausingEventMetadata().getType().getName()).isEqualTo("eventName");
        assertThat(result.getErrors().get(4).getCausingEventMetadata().getType().getName()).isEqualTo("eventName2");
    }

    @Test
    void testCacheManager() {
        service.getAllEventSources();
        service.getAllErrorCodes();
        service.getAllEventNames();

        assertNotNull(cacheManager);
        assertInstanceOf(CaffeineCacheManager.class, cacheManager);
        assertEquals(3, cacheManager.getCacheNames().size());
        assertTrue(cacheManager.getCacheNames().contains("eventSources"));
        assertTrue(cacheManager.getCacheNames().contains("errorCodes"));
        assertTrue(cacheManager.getCacheNames().contains("eventNames"));
    }

    @Test
    void search_withParams_listOfStates_twoStates_errorsFound() {

        saveError(Error.ErrorState.PERMANENT_RETRIED, "service", "eventName", ZonedDateTime.now().minusDays(3), "123", "myTraceId");
        saveError(Error.ErrorState.PERMANENT, "service", "eventName", ZonedDateTime.now().minusDays(1), "123", "myTraceId");
        saveError(Error.ErrorState.PERMANENT, "service", "eventName", ZonedDateTime.now().minusDays(2), "123", "myTraceId");
        storeErrors();

        ErrorSearchCriteria criteria = ErrorSearchCriteria.builder()
                .states(List.of(Error.ErrorState.PERMANENT, Error.ErrorState.PERMANENT_RETRIED))
                .sort(new String[]{"created,desc"}).build();
        ErrorList errorList = service.search(criteria);
        assertThat(errorList.getTotalElements()).isEqualTo(7);
    }

    @Test
    void search_withParams_listOfStates_twoStates_errorsNotFound() {

        saveError(Error.ErrorState.PERMANENT_RETRIED, "service", "eventName", ZonedDateTime.now().minusDays(3), "123", "myTraceId");
        saveError(Error.ErrorState.PERMANENT, "service", "eventName", ZonedDateTime.now().minusDays(1), "123", "myTraceId");
        saveError(Error.ErrorState.PERMANENT, "service", "eventName", ZonedDateTime.now().minusDays(2), "123", "myTraceId");
        storeErrors();

        ErrorSearchCriteria criteria = ErrorSearchCriteria.builder()
                .states(List.of(Error.ErrorState.DELETED, Error.ErrorState.RESOLVE_ON_MANUALTASK))
                .sort(new String[]{"created,desc"}).build();
        ErrorList errorList = service.search(criteria);
        assertThat(errorList.getTotalElements()).isZero();
    }

    @Test
    void search_withClosingReason_isSoughtAsALike_errorsFound() {

        saveError(Error.ErrorState.DELETED, "service", "eventName", ZonedDateTime.now().minusDays(1), "1234", "myTraceId", "JEAP-12345");
        saveError(Error.ErrorState.DELETED, "service", "eventName", ZonedDateTime.now().minusDays(2), "123", "myTraceId2", "JEAP-123");
        storeErrors();

        ErrorSearchCriteria criteria = ErrorSearchCriteria.builder()
                .states(List.of(Error.ErrorState.DELETED, Error.ErrorState.RESOLVE_ON_MANUALTASK))
                .closingReason("JEAP-123")
                .sort(new String[]{"created,desc"}).build();
        ErrorList errorList = service.search(criteria);
        assertThat(errorList.getTotalElements()).isEqualTo(2);

        ErrorSearchCriteria criteria1 = ErrorSearchCriteria.builder()
                .states(List.of(Error.ErrorState.DELETED, Error.ErrorState.RESOLVE_ON_MANUALTASK))
                .closingReason("JEAP-12345")
                .sort(new String[]{"created,desc"}).build();
        ErrorList errorList1 = service.search(criteria1);
        assertThat(errorList1.getTotalElements()).isEqualTo(1);
        assertThat(errorList1.getErrors().getFirst().getClosingReason()).isEqualTo("JEAP-12345");
    }


    private void storeErrors() {
        saveError(Error.ErrorState.TEMPORARY_RETRIED, "service", "eventName", ZonedDateTime.now(), "123", "myTraceId");
        saveError(Error.ErrorState.PERMANENT, "service", "eventName", ZonedDateTime.now(), "321", "myTraceId");
        saveError(Error.ErrorState.PERMANENT, "service", "eventName2", ZonedDateTime.now(), "123", "myTraceId");
        saveError(Error.ErrorState.PERMANENT, "service2", "eventName", ZonedDateTime.now(), "123", "myTraceId");
        saveError(Error.ErrorState.PERMANENT, "service", "eventName", ZonedDateTime.now().minusDays(2), "123", "myTraceIdNew");
    }

    private Error saveError(Error.ErrorState errorState, String serviceName, String eventName, ZonedDateTime created, String errorCode, String traceId) {
        EventMetadata metadata = getEventMetadata(serviceName, eventName);
        CausingEvent causingEvent = saveCausingEvent(metadata);
        return storeError(metadata, causingEvent, errorState, created, errorCode, traceId, "because this is a test");
    }

    private Error saveError(Error.ErrorState errorState, String serviceName, String eventName, ZonedDateTime created, String errorCode, String traceId, String closingReason) {
        EventMetadata metadata = getEventMetadata(serviceName, eventName);
        CausingEvent causingEvent = saveCausingEvent(metadata);
        return storeError(metadata, causingEvent, errorState, created, errorCode, traceId, closingReason);
    }

    private CausingEvent saveCausingEvent(EventMetadata metadata) {
        CausingEvent causingEvent = CausingEvent.builder()
                .message(EventMessage.builder()
                        .offset(1)
                        .payload("test".getBytes(StandardCharsets.UTF_8))
                        .topic("topic")
                        .clusterName("clusterName")
                        .build())
                .metadata(metadata)
                .build();
        causingEventRepository.save(causingEvent);
        return causingEvent;
    }

    private EventMetadata getEventMetadata(String serviceName, String eventName) {
        return EventMetadata.builder()
                .id(UUID.randomUUID().toString())
                .created(ZonedDateTime.now())
                .idempotenceId(UUID.randomUUID().toString())
                .publisher(EventPublisher.builder()
                        .service(serviceName)
                        .system("system")
                        .build())
                .type(EventType.builder()
                        .name(eventName)
                        .version("1.0.0")
                        .build())
                .build();
    }

    private Error storeError(EventMetadata metadata, CausingEvent causingEvent, Error.ErrorState temporaryRetried, ZonedDateTime created, String errorCode, String traceId, String closingReason) {
        Error error = Error.builder()
                .state(temporaryRetried)
                .causingEvent(causingEvent)
                .errorEventData(ErrorEventData.builder()
                        .code(errorCode)
                        .temporality(ErrorEventData.Temporality.PERMANENT)
                        .message("test")
                        .build())
                .errorEventMetadata(metadata)
                .closingReason(closingReason)
                .originalTraceContext(OriginalTraceContext.builder()
                        .traceIdString(traceId)
                        .build())
                .created(created)
                .build();
        return errorRepository.save(error);
    }
}
