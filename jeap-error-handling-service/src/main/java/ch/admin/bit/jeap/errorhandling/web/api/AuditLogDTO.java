package ch.admin.bit.jeap.errorhandling.web.api;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.AuditLog;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor // for Jackson
@AllArgsConstructor
public class AuditLogDTO {

    @NotNull
    private String authContext;
    @NotNull
    private String subject;
    @NotNull
    private AuditLog.AuditedAction action;
    @NotNull
    private String created;
    private String extId;
    private String givenName;
    private String familyName;
}
