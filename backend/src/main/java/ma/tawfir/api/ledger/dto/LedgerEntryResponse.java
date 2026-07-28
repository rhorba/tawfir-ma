package ma.tawfir.api.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ma.tawfir.api.ledger.entity.LedgerEntryType;
import ma.tawfir.api.ledger.entity.LedgerSource;

public record LedgerEntryResponse(
	UUID id,
	UUID groupId,
	LedgerEntryType entryType,
	UUID contributionScheduleId,
	UUID payoutScheduleId,
	UUID actorUserId,
	BigDecimal amount,
	LedgerSource source,
	UUID reversalOfEntryId,
	Instant createdAt
) {
}
