package ma.tawfir.api.webhook;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import ma.tawfir.api.auth.JwtService;
import ma.tawfir.api.group.ContributionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CmiWebhookController.class, excludeAutoConfiguration = {
	SecurityAutoConfiguration.class,
	UserDetailsServiceAutoConfiguration.class,
	SecurityFilterAutoConfiguration.class,
	ServletWebSecurityAutoConfiguration.class
})
class CmiWebhookControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CmiSignatureVerifier signatureVerifier;

	@MockitoBean
	private ContributionService contributionService;

	@MockitoBean
	private JwtService jwtService;

	@Test
	void paymentConfirmation_validSignature_confirmsAndReturns200() throws Exception {
		UUID scheduleId = UUID.randomUUID();
		String body = "{\"contributionScheduleId\":\"" + scheduleId + "\",\"amount\":200,\"providerReference\":\"MOCK-1\"}";
		when(signatureVerifier.isValid(eq(body), eq("valid-sig"))).thenReturn(true);

		mockMvc.perform(post("/api/v1/webhooks/cmi/payment-confirmation")
				.header("X-Cmi-Signature", "valid-sig")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk());

		verify(contributionService).confirmViaWebhook(scheduleId, BigDecimal.valueOf(200));
	}

	@Test
	void paymentConfirmation_invalidSignature_returns401AndDoesNotCallService() throws Exception {
		String body = "{\"contributionScheduleId\":\"" + UUID.randomUUID() + "\",\"amount\":200}";
		when(signatureVerifier.isValid(eq(body), eq("bad-sig"))).thenReturn(false);

		mockMvc.perform(post("/api/v1/webhooks/cmi/payment-confirmation")
				.header("X-Cmi-Signature", "bad-sig")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isUnauthorized());

		verify(contributionService, never()).confirmViaWebhook(any(), any());
	}

	@Test
	void paymentConfirmation_missingSignatureHeader_returns401() throws Exception {
		String body = "{\"contributionScheduleId\":\"" + UUID.randomUUID() + "\",\"amount\":200}";
		when(signatureVerifier.isValid(eq(body), eq(null))).thenReturn(false);

		mockMvc.perform(post("/api/v1/webhooks/cmi/payment-confirmation")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void paymentConfirmation_validSignatureButMalformedJson_returns400() throws Exception {
		String body = "not-json";
		when(signatureVerifier.isValid(eq(body), eq("valid-sig"))).thenReturn(true);

		mockMvc.perform(post("/api/v1/webhooks/cmi/payment-confirmation")
				.header("X-Cmi-Signature", "valid-sig")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest());

		verify(contributionService, never()).confirmViaWebhook(any(), any());
	}

}
