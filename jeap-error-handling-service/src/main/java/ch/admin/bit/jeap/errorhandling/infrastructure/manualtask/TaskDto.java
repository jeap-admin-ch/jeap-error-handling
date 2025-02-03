package ch.admin.bit.jeap.errorhandling.infrastructure.manualtask;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Value
@Builder(toBuilder = true)
@RequiredArgsConstructor
@NonFinal
public class TaskDto {

    UUID id;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime due;

    String priority;

    String type;

    TaskStatus state;

    String system;

    String service;

    @Singular
    List<TaskReferenceDto> references;

    @Singular
    List<TaskDetailDto> additionalDetails;
}
