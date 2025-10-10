package ch.admin.bit.jeap.errorhandling.domain.manualtask.taskFactory;

import ch.admin.bit.jeap.errorhandling.infrastructure.manualtask.*;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventMetadata;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {DefaultTaskFactory.class, DefaultTaskFactoryProperties.class},
        properties = {"jeap.errorhandling.task-management.default-factory.system=testsystem",
                "jeap.errorhandling.task-management.default-factory.errorServiceBaseUrl=http://localhost/"})
@EnableConfigurationProperties
class DefaultTaskFactoryTest {

    @MockitoBean
    private TaskManagementServiceProperties taskManagementServiceProperties;
    @MockitoBean
    private TaskManagementClient taskManagementClient;
    @Autowired
    private DefaultTaskFactory target;
    @Captor
    private ArgumentCaptor<List<TaskTypDto>> typDtoArgumentCaptor;

    @Test
    void create() {
        EventPublisher publisher = mock(EventPublisher.class);
        when(publisher.getService()).thenReturn("test");

        EventMetadata causingEventMetadata = mock(EventMetadata.class);
        when(causingEventMetadata.getPublisher()).thenReturn(publisher);

        Error error = mock(Error.class);
        UUID errorId = UUID.randomUUID();
        when(error.getId()).thenReturn(errorId);
        when(error.getCausingEventMetadata()).thenReturn(causingEventMetadata);

        TaskDto result = target.create(error);

        assertEquals(TaskStatus.OPEN, result.getState());
        assertEquals("test", result.getService());
        assertEquals("http://localhost/" + error.getId(), result.getReferences().get(0).getUri());
        assertEquals("Error Service", result.getReferences().get(0).getName());
        assertEquals("testsystem", result.getSystem());
    }

    @Test
    void createTaskTypes() {
        when(taskManagementServiceProperties.isEnabled()).thenReturn(true);
        doNothing().when(taskManagementClient).lazilyInitializeTaskTypes(typDtoArgumentCaptor.capture());

        target.createTaskTypes();

        assertEquals(1, typDtoArgumentCaptor.getValue().size());
        TaskTypDto taskTypDto = typDtoArgumentCaptor.getValue().get(0);
        assertEquals("testsystem", taskTypDto.getSystem());
        assertEquals("errorhandling", taskTypDto.getName());
        assertEquals("error-handling", taskTypDto.getDomain());
    }
}
