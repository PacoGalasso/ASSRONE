package ASSRONE.backend.dto;

import ASSRONE.backend.model.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RoleChangeRequest {

    @NotNull(message = "Role required")
    private UserRole role;
}
