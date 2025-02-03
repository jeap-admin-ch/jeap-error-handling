package ch.admin.bit.jeap.errorhandling.infrastructure.manualtask;

import lombok.*;

@Value
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TaskTypeDisplayDto {

    @NonNull
    String language;

    @NonNull
    String displayName;

    @NonNull
    String displayDomain;

    @NonNull
    String title;

    @NonNull
    String description;
}
