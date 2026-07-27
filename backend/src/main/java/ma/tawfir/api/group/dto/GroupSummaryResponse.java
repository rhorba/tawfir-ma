package ma.tawfir.api.group.dto;

import java.math.BigDecimal;
import java.util.UUID;
import ma.tawfir.api.group.entity.Frequency;
import ma.tawfir.api.group.entity.GroupStatus;

public record GroupSummaryResponse(
	UUID id,
	String name,
	BigDecimal contributionAmount,
	String currency,
	Frequency frequency,
	short totalCycles,
	GroupStatus status,
	int memberCount
) {
}
