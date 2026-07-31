package ma.tawfir.api.mfa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MfaVerifyRequest(

	@NotBlank
	@Pattern(regexp = "^\\d{6}$", message = "code must be 6 digits")
	String code

) {
}
