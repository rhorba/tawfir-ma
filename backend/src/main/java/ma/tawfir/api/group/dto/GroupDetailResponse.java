package ma.tawfir.api.group.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ma.tawfir.api.group.entity.Frequency;
import ma.tawfir.api.group.entity.GroupStatus;
import ma.tawfir.api.group.entity.PayoutOrderMode;

public record GroupDetailResponse(
	UUID id,
	String name,
	UUID organizerId,
	BigDecimal contributionAmount,
	String currency,
	Frequency frequency,
	short totalCycles,
	PayoutOrderMode payoutOrderMode,
	GroupStatus status,
	List<MemberResponse> members
) {
}
