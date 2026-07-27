package ma.tawfir.api.group.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import ma.tawfir.api.group.entity.Frequency;
import ma.tawfir.api.group.entity.PayoutOrderMode;

/**
 * `members` is every participant's phone number, including the Organizer's own
 * (if omitted, the Organizer is auto-appended). For MANUAL payout order, array
 * position (index + 1) becomes each member's payout_position — see decisions.md
 * 2026-07-27.
 */
public record CreateGroupRequest(

	@NotBlank
	String name,

	@NotNull
	@DecimalMin(value = "0.0", inclusive = false)
	BigDecimal contributionAmount,

	@NotNull
	Frequency frequency,

	@NotNull
	@Positive
	Short totalCycles,

	@NotNull
	PayoutOrderMode payoutOrderMode,

	@NotEmpty
	List<@NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "each member phoneNumber must be in E.164 format") String> members

) {
}
