package ma.tawfir.api.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.tawfir.api.common.ValidationException;
import ma.tawfir.api.group.ContributionService;
import ma.tawfir.api.webhook.dto.PaymentConfirmationPayload;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * System-to-system CMI callbacks (stories 3.3/4.3) — authenticated by HMAC
 * signature (CmiSignatureVerifier), not JWT; public in SecurityConfig for
 * that reason. Takes the raw body as a String rather than binding straight to
 * a DTO so the exact bytes CMI signed are what gets verified, then parses it
 * manually once the signature checks out.
 */
@RestController
@RequestMapping("/api/v1/webhooks/cmi")
public class CmiWebhookController {

	private final CmiSignatureVerifier signatureVerifier;
	private final ContributionService contributionService;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public CmiWebhookController(CmiSignatureVerifier signatureVerifier, ContributionService contributionService) {
		this.signatureVerifier = signatureVerifier;
		this.contributionService = contributionService;
	}

	@PostMapping("/payment-confirmation")
	@ResponseStatus(HttpStatus.OK)
	public void paymentConfirmation(@RequestBody String rawBody,
			@RequestHeader(value = "X-Cmi-Signature", required = false) String signature) {
		requireValidSignature(rawBody, signature);
		PaymentConfirmationPayload payload = parse(rawBody, PaymentConfirmationPayload.class);
		contributionService.confirmViaWebhook(payload.contributionScheduleId(), payload.amount());
	}

	private void requireValidSignature(String rawBody, String signature) {
		if (!signatureVerifier.isValid(rawBody, signature)) {
			throw new InvalidWebhookSignatureException("Invalid or missing CMI webhook signature");
		}
	}

	private <T> T parse(String rawBody, Class<T> type) {
		try {
			return objectMapper.readValue(rawBody, type);
		} catch (Exception e) {
			throw new ValidationException("Malformed webhook payload");
		}
	}

}
