package ASSRONE.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenRequest {

    @NotBlank(message = "Le refresh token est obligatoire")
    private String refreshToken;
}
