package ma.tawfir.api.admin.dto;

import java.util.UUID;
import ma.tawfir.api.group.entity.GroupStatus;

public record AdminGroupResponse(
	UUID id,
	String name,
	GroupStatus status,
	int memberCount,
	short totalCycles,
	int cyclesCompleted
) {
}
