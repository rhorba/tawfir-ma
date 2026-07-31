package ma.tawfir.api.webhook.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentConfirmationPayload(
	UUID contributionScheduleId,
	BigDecimal amount,
	String providerReference
) {
}
