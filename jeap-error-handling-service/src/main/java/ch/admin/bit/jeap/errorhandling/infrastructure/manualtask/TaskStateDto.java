package ch.admin.bit.jeap.errorhandling.infrastructure.manualtask;

import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
public class TaskStateDto {

    TaskStatus state;
}
