package ma.tawfir.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MfaLoginVerifyRequest(

	@NotBlank
	String mfaPendingToken,

	@NotBlank
	@Pattern(regexp = "^\\d{6}$", message = "code must be 6 digits")
	String code

) {
}
