package ma.tawfir.api.mfa;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * RFC 6238 TOTP (HMAC-SHA1, 30s step, 6 digits) plus RFC 4648 Base32 encoding
 * for the secret, hand-rolled rather than adding a third-party TOTP dependency
 * — the algorithm is small and fully specified, and doing it in-house keeps
 * the secret/time handling fully under this codebase's control for testing
 * (no library-imposed Clock/formatter indirection to work around).
 *
 * <p>Deliberately no backup/recovery codes (story 1.4 / stories-tawfir.md):
 * a locked-out admin is recovered via direct DB action by another admin/ops,
 * an accepted MVP limitation per the Sprint 5 brainstorm decision.
 */
@Component
public class TotpGenerator {

	private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
	private static final String HMAC_ALGORITHM = "HmacSHA1";
	private static final int TIME_STEP_SECONDS = 30;
	private static final int CODE_DIGITS = 6;
	private static final int ALLOWED_SKEW_STEPS = 1;
	private static final int SECRET_LENGTH_BYTES = 20;
	private static final String ISSUER = "Tawfir.ma";

	private final SecureRandom secureRandom = new SecureRandom();

	/** A fresh random Base32-encoded 160-bit secret, per RFC 4648 as required by TOTP. */
	public String generateSecret() {
		byte[] bytes = new byte[SECRET_LENGTH_BYTES];
		secureRandom.nextBytes(bytes);
		return base32Encode(bytes);
	}

	/** An {@code otpauth://totp/...} provisioning URI for client-side QR code generation. */
	public String buildProvisioningUri(String accountLabel, String base32Secret) {
		String encodedIssuer = urlEncode(ISSUER);
		String encodedLabel = urlEncode(accountLabel);
		return "otpauth://totp/" + encodedIssuer + ":" + encodedLabel
			+ "?secret=" + base32Secret
			+ "&issuer=" + encodedIssuer
			+ "&algorithm=SHA1&digits=" + CODE_DIGITS + "&period=" + TIME_STEP_SECONDS;
	}

	/** The 6-digit code for {@code base32Secret} at the time step containing {@code at}. */
	public String generate(String base32Secret, Instant at) {
		return generateCode(base32Secret, at.getEpochSecond() / TIME_STEP_SECONDS);
	}

	/** True if {@code code} matches any step within ±1 (30s) of {@code now}, per RFC 6238 clock-skew tolerance. */
	public boolean verify(String base32Secret, String code, Instant now) {
		long currentStep = now.getEpochSecond() / TIME_STEP_SECONDS;
		for (long step = currentStep - ALLOWED_SKEW_STEPS; step <= currentStep + ALLOWED_SKEW_STEPS; step++) {
			if (generateCode(base32Secret, step).equals(code)) {
				return true;
			}
		}
		return false;
	}

	private String generateCode(String base32Secret, long timeStep) {
		byte[] key = base32Decode(base32Secret);
		byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
			byte[] hash = mac.doFinal(data);

			int offset = hash[hash.length - 1] & 0x0F;
			int binary = ((hash[offset] & 0x7F) << 24)
				| ((hash[offset + 1] & 0xFF) << 16)
				| ((hash[offset + 2] & 0xFF) << 8)
				| (hash[offset + 3] & 0xFF);
			int otp = binary % (int) Math.pow(10, CODE_DIGITS);
			return String.format(Locale.ROOT, "%0" + CODE_DIGITS + "d", otp);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(HMAC_ALGORITHM + " must be available on the JVM", e);
		}
	}

	private static String urlEncode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static String base32Encode(byte[] data) {
		StringBuilder result = new StringBuilder();
		int buffer = 0;
		int bitsLeft = 0;
		for (byte b : data) {
			buffer = (buffer << 8) | (b & 0xFF);
			bitsLeft += 8;
			while (bitsLeft >= 5) {
				int index = (buffer >> (bitsLeft - 5)) & 0x1F;
				result.append(BASE32_ALPHABET.charAt(index));
				bitsLeft -= 5;
			}
		}
		if (bitsLeft > 0) {
			int index = (buffer << (5 - bitsLeft)) & 0x1F;
			result.append(BASE32_ALPHABET.charAt(index));
		}
		return result.toString();
	}

	private static byte[] base32Decode(String encoded) {
		String clean = encoded.trim().toUpperCase(Locale.ROOT).replace("=", "");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int buffer = 0;
		int bitsLeft = 0;
		for (char c : clean.toCharArray()) {
			int index = BASE32_ALPHABET.indexOf(c);
			if (index < 0) {
				continue;
			}
			buffer = (buffer << 5) | index;
			bitsLeft += 5;
			if (bitsLeft >= 8) {
				out.write((buffer >> (bitsLeft - 8)) & 0xFF);
				bitsLeft -= 8;
			}
		}
		return out.toByteArray();
	}

}
