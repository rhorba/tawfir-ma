package ma.tawfir.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpRequestRequest(

	@NotBlank
	@Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "phoneNumber must be in E.164 format")
	String phoneNumber

) {
}
