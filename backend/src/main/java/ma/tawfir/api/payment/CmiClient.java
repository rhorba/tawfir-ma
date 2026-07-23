package ma.tawfir.api.payment;

import java.math.BigDecimal;

/**
 * Outbound calls to CMI (Morocco's payment rail). Real integration is gated on the
 * custody-model decision (system-design-tawfir.md SDR-3) and doesn't start until
 * Sprint 4 (stories 3.3/4.1-4.3) — {@link MockCmiClient} stands in until then.
 */
public interface CmiClient {

	/**
	 * @return a provider-assigned reference id for the initiated transfer
	 */
	String initiateTransfer(String reference, BigDecimal amount);

}
