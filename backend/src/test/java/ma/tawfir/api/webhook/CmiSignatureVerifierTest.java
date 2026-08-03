package ma.tawfir.api.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import ma.tawfir.api.config.TawfirProperties;
import org.junit.jupiter.api.Test;

class CmiSignatureVerifierTest {

	private static final String SECRET = "test-webhook-secret";

	private final CmiSignatureVerifier verifier =
		new CmiSignatureVerifier(new TawfirProperties(null, null, new TawfirProperties.Payment("mock", SECRET), null, null, null));

	private static String hmacHex(String secret, String body) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
		StringBuilder hex = new StringBuilder();
		for (byte b : hash) {
			hex.append(String.format("%02x", b));
		}
		return hex.toString();
	}

	@Test
	void isValid_correctSignature_true() throws Exception {
		String body = "{\"contributionScheduleId\":\"abc\"}";
		String signature = hmacHex(SECRET, body);

		assertThat(verifier.isValid(body, signature)).isTrue();
	}

	@Test
	void isValid_tamperedBody_false() throws Exception {
		String body = "{\"contributionScheduleId\":\"abc\"}";
		String signature = hmacHex(SECRET, body);

		assertThat(verifier.isValid(body + "x", signature)).isFalse();
	}

	@Test
	void isValid_wrongSecret_false() throws Exception {
		String body = "{\"contributionScheduleId\":\"abc\"}";
		String signature = hmacHex("a-different-secret", body);

		assertThat(verifier.isValid(body, signature)).isFalse();
	}

	@Test
	void isValid_missingSignature_false() {
		assertThat(verifier.isValid("{}", null)).isFalse();
	}

	@Test
	void isValid_blankSignature_false() {
		assertThat(verifier.isValid("{}", "   ")).isFalse();
	}

	@Test
	void isValid_garbageSignature_false() {
		assertThat(verifier.isValid("{}", "not-a-real-signature")).isFalse();
	}

}
