package ma.tawfir.api.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OpenDisputeRequest(

	@NotNull
	UUID ledgerEntryId,

	@NotBlank
	String reason,

	String evidenceNote

) {
}
