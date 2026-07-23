package ma.tawfir.api.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MockCmiClientTest {

	private final MockCmiClient client = new MockCmiClient();

	@Test
	void initiateTransfer_returnsMockReference() {
		String ref = client.initiateTransfer("contribution-123", new BigDecimal("500.00"));

		assertThat(ref).startsWith("MOCK-");
	}

	@Test
	void initiateTransfer_returnsUniqueReferencesPerCall() {
		String ref1 = client.initiateTransfer("contribution-123", BigDecimal.TEN);
		String ref2 = client.initiateTransfer("contribution-123", BigDecimal.TEN);

		assertThat(ref1).isNotEqualTo(ref2);
	}

}
