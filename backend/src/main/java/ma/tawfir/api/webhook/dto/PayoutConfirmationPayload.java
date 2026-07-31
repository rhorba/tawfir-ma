package ma.tawfir.api.webhook.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PayoutConfirmationPayload(
	UUID payoutScheduleId,
	BigDecimal amount,
	String providerReference
) {
}
