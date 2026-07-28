package ma.tawfir.api.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ma.tawfir.api.common.ForbiddenException;
import ma.tawfir.api.group.GroupMembershipRepository;
import ma.tawfir.api.ledger.dto.LedgerEntryResponse;
import ma.tawfir.api.ledger.entity.LedgerEntry;
import ma.tawfir.api.ledger.entity.LedgerSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

	@Mock
	private LedgerEntryRepository ledgerEntryRepository;
	@Mock
	private GroupMembershipRepository membershipRepository;

	private LedgerService ledgerService;

	@BeforeEach
	void setUp() {
		ledgerService = new LedgerService(ledgerEntryRepository, membershipRepository);
	}

	@Test
	void listForGroup_member_returnsEntries() {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(membershipRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(true);
		LedgerEntry entry = LedgerEntry.contribution(
			groupId, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, LedgerSource.ORGANIZER_CONFIRMED);
		when(ledgerEntryRepository.findByGroupIdOrderByCreatedAtAsc(groupId)).thenReturn(List.of(entry));

		List<LedgerEntryResponse> result = ledgerService.listForGroup(userId, groupId, false);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).groupId()).isEqualTo(groupId);
	}

	@Test
	void listForGroup_nonMember_throwsForbidden() {
		UUID groupId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(membershipRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(false);

		assertThatThrownBy(() -> ledgerService.listForGroup(userId, groupId, false))
			.isInstanceOf(ForbiddenException.class);
		verify(ledgerEntryRepository, never()).findByGroupIdOrderByCreatedAtAsc(groupId);
	}

	@Test
	void listForGroup_admin_bypassesMembershipCheck() {
		UUID groupId = UUID.randomUUID();
		UUID adminId = UUID.randomUUID();
		when(ledgerEntryRepository.findByGroupIdOrderByCreatedAtAsc(groupId)).thenReturn(List.of());

		ledgerService.listForGroup(adminId, groupId, true);

		verify(membershipRepository, never()).existsByGroupIdAndUserId(groupId, adminId);
	}

}
