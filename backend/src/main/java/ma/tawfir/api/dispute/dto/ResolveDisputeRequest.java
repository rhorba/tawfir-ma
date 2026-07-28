package ma.tawfir.api.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ma.tawfir.api.dispute.entity.DisputeStatus;

public record ResolveDisputeRequest(

	@NotNull
	DisputeStatus resolution,

	@NotBlank
	String resolutionReason

) {
}
