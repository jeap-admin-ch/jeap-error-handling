package ch.admin.bit.jeap.errorhandling.infrastructure.manualtask;

import lombok.*;


@Value
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TaskDetailDto {

    @NonNull
    String name;

    @NonNull
    String value;
}
