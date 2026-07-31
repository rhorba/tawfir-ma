package ma.tawfir.api.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import ma.tawfir.api.config.TawfirProperties;
import org.springframework.stereotype.Component;

/**
 * Verifies the {@code X-Cmi-Signature} header on inbound CMI webhooks
 * (stories 3.3/4.3, decisions.md 2026-07-31): hex(HMAC-SHA256(raw request
 * body, CMI_WEBHOOK_SECRET)), compared in constant time. No real CMI API
 * spec is available (this project has no production CMI integration yet,
 * gated on SDR-3) — this is a from-scratch, documented contract, not a spec
 * transcription.
 */
@Component
public class CmiSignatureVerifier {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

	private final String webhookSecret;

	public CmiSignatureVerifier(TawfirProperties properties) {
		this.webhookSecret = properties.payment().webhookSecret();
	}

	/** True if {@code signatureHeader} is a valid hex-encoded HMAC-SHA256 of {@code rawBody}. */
	public boolean isValid(String rawBody, String signatureHeader) {
		if (signatureHeader == null || signatureHeader.isBlank()) {
			return false;
		}
		String expected = computeSignature(rawBody);
		byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
		byte[] providedBytes = signatureHeader.trim().getBytes(StandardCharsets.US_ASCII);
		return MessageDigest.isEqual(expectedBytes, providedBytes);
	}

	private String computeSignature(String rawBody) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			byte[] hash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
			return toHex(hash);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException(HMAC_ALGORITHM + " must be available on the JVM", e);
		}
	}

	private static String toHex(byte[] bytes) {
		char[] out = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++) {
			int value = bytes[i] & 0xFF;
			out[i * 2] = HEX_CHARS[value >>> 4];
			out[i * 2 + 1] = HEX_CHARS[value & 0x0F];
		}
		return new String(out);
	}

}
