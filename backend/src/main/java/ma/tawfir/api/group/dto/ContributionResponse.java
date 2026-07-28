package ma.tawfir.api.group.dto;

import java.time.LocalDate;
import java.util.UUID;
import ma.tawfir.api.group.entity.ContributionStatus;

public record ContributionResponse(
	UUID id,
	UUID groupId,
	short cycleNumber,
	UUID userId,
	LocalDate dueDate,
	ContributionStatus status
) {
}
