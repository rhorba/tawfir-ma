package ma.tawfir.api.mfa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpGeneratorTest {

	// RFC 6238 Appendix B test vector: 20-byte ASCII seed "12345678901234567890",
	// Base32-encoded, HMAC-SHA1, T=59s -> code 287082.
	private static final String RFC_TEST_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

	private final TotpGenerator totpGenerator = new TotpGenerator();

	@Test
	void generate_matchesRfc6238TestVectorAtFixedTime() {
		String code = totpGenerator.generate(RFC_TEST_SECRET, Instant.ofEpochSecond(59));

		assertThat(code).isEqualTo("287082");
	}

	@Test
	void generate_isSixDigitsZeroPadded() {
		String code = totpGenerator.generate(totpGenerator.generateSecret(), Instant.now());

		assertThat(code).hasSize(6).matches("\\d{6}");
	}

	@Test
	void verify_currentCode_succeeds() {
		String secret = totpGenerator.generateSecret();
		Instant now = Instant.now();
		String code = totpGenerator.generate(secret, now);

		assertThat(totpGenerator.verify(secret, code, now)).isTrue();
	}

	@Test
	void verify_codeOneStepInPast_succeedsWithinSkewTolerance() {
		String secret = totpGenerator.generateSecret();
		Instant now = Instant.ofEpochSecond(1_000_000_000L);
		String previousStepCode = totpGenerator.generate(secret, now.minusSeconds(30));

		assertThat(totpGenerator.verify(secret, previousStepCode, now)).isTrue();
	}

	@Test
	void verify_codeOneStepInFuture_succeedsWithinSkewTolerance() {
		String secret = totpGenerator.generateSecret();
		Instant now = Instant.ofEpochSecond(1_000_000_000L);
		String nextStepCode = totpGenerator.generate(secret, now.plusSeconds(30));

		assertThat(totpGenerator.verify(secret, nextStepCode, now)).isTrue();
	}

	@Test
	void verify_codeTwoStepsAway_failsOutsideSkewTolerance() {
		String secret = totpGenerator.generateSecret();
		Instant now = Instant.ofEpochSecond(1_000_000_000L);
		String twoStepsAheadCode = totpGenerator.generate(secret, now.plusSeconds(60));

		assertThat(totpGenerator.verify(secret, twoStepsAheadCode, now)).isFalse();
	}

	@Test
	void verify_wrongCode_fails() {
		String secret = totpGenerator.generateSecret();

		assertThat(totpGenerator.verify(secret, "000000", Instant.now())).isFalse();
	}

	@Test
	void generateSecret_producesDifferentSecretsEachTime() {
		String first = totpGenerator.generateSecret();
		String second = totpGenerator.generateSecret();

		assertThat(first).isNotEqualTo(second);
		assertThat(first).matches("[A-Z2-7]+");
	}

	@Test
	void buildProvisioningUri_includesSecretIssuerAndAccountLabel() {
		String uri = totpGenerator.buildProvisioningUri("+212612345678", "SECRETVALUE");

		assertThat(uri).startsWith("otpauth://totp/Tawfir.ma:%2B212612345678");
		assertThat(uri).contains("secret=SECRETVALUE");
		assertThat(uri).contains("issuer=Tawfir.ma");
		assertThat(uri).contains("algorithm=SHA1");
		assertThat(uri).contains("digits=6");
		assertThat(uri).contains("period=30");
	}

}
