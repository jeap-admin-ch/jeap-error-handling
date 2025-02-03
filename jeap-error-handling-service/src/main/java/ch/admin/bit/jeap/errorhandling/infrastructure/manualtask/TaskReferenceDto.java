package ch.admin.bit.jeap.errorhandling.infrastructure.manualtask;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TaskReferenceDto {

    String name;

    String uri;
}
