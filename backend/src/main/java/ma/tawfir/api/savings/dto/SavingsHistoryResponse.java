package ma.tawfir.api.savings.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SavingsHistoryResponse(
	UUID groupId,
	short cyclesCompleted,
	BigDecimal onTimeRate,
	short disputesInvolved,
	Instant computedAt
) {
}
