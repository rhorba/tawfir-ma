package ma.tawfir.api.dispute.dto;

import java.time.Instant;
import java.util.UUID;
import ma.tawfir.api.dispute.entity.DisputeStatus;

public record DisputeResponse(
	UUID id,
	UUID groupId,
	UUID ledgerEntryId,
	UUID raisedByUserId,
	String reason,
	String evidenceNote,
	DisputeStatus status,
	UUID resolvedByUserId,
	String resolutionReason,
	Instant createdAt,
	Instant resolvedAt
) {
}
