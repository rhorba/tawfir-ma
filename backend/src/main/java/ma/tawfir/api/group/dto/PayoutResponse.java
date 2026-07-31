package ma.tawfir.api.group.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import ma.tawfir.api.group.entity.PayoutStatus;

public record PayoutResponse(
	UUID id,
	UUID groupId,
	short cycleNumber,
	UUID recipientId,
	LocalDate scheduledDate,
	BigDecimal amount,
	PayoutStatus status
) {
}
