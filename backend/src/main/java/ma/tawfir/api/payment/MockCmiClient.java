package ma.tawfir.api.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Local-dev/test stand-in for CMI: logs the request and returns a fake reference
 * instead of calling a real payment rail. Active by default ({@code tawfir.payment.provider=mock}).
 */
@Service
@ConditionalOnProperty(prefix = "tawfir.payment", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockCmiClient implements CmiClient {

	private static final Logger log = LoggerFactory.getLogger(MockCmiClient.class);

	@Override
	public String initiateTransfer(String reference, BigDecimal amount) {
		String fakeProviderRef = "MOCK-" + UUID.randomUUID();
		log.info("[MOCK CMI] initiateTransfer reference={} amount={} -> providerRef={}", reference, amount, fakeProviderRef);
		return fakeProviderRef;
	}

}
