package ma.tawfir.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(

	@NotBlank
	@Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "phoneNumber must be in E.164 format")
	String phoneNumber,

	@NotBlank
	@Pattern(regexp = "^\\d{6}$", message = "code must be 6 digits")
	String code

) {
}
