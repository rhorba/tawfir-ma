package ma.tawfir.api.ledger;

import java.util.List;
import java.util.UUID;
import ma.tawfir.api.common.ForbiddenException;
import ma.tawfir.api.group.GroupMembershipRepository;
import ma.tawfir.api.ledger.dto.LedgerEntryResponse;
import ma.tawfir.api.ledger.entity.LedgerEntry;
import org.springframework.stereotype.Service;

@Service
public class LedgerService {

	private final LedgerEntryRepository ledgerEntryRepository;
	private final GroupMembershipRepository membershipRepository;

	public LedgerService(LedgerEntryRepository ledgerEntryRepository, GroupMembershipRepository membershipRepository) {
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.membershipRepository = membershipRepository;
	}

	public List<LedgerEntryResponse> listForGroup(UUID actingUserId, UUID groupId, boolean isAdmin) {
		if (!isAdmin && !membershipRepository.existsByGroupIdAndUserId(groupId, actingUserId)) {
			throw new ForbiddenException("You are not a member of this group");
		}
		return ledgerEntryRepository.findByGroupIdOrderByCreatedAtAsc(groupId).stream()
			.map(this::toResponse)
			.toList();
	}

	private LedgerEntryResponse toResponse(LedgerEntry entry) {
		return new LedgerEntryResponse(entry.getId(), entry.getGroupId(), entry.getEntryType(),
			entry.getContributionScheduleId(), entry.getPayoutScheduleId(), entry.getActorUserId(),
			entry.getAmount(), entry.getSource(), entry.getReversalOfEntryId(), entry.getCreatedAt());
	}

}
