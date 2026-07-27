package ma.tawfir.api.group.dto;

import java.util.UUID;
import ma.tawfir.api.group.entity.MembershipRole;

public record MemberResponse(
	UUID userId,
	String phoneNumber,
	MembershipRole roleInGroup,
	Short payoutPosition
) {
}
