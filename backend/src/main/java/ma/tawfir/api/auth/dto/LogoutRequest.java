package ma.tawfir.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
	@NotBlank String refreshToken
) {
}
