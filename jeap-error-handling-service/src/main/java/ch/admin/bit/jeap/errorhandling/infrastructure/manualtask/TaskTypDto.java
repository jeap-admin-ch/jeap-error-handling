package ch.admin.bit.jeap.errorhandling.infrastructure.manualtask;

import lombok.*;

import java.util.List;

@Value
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TaskTypDto {

    @NonNull
    String system;

    @NonNull
    String name;

    @NonNull
    String domain;

    @NonNull
    List<TaskTypeDisplayDto> display;
}
