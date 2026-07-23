package ma.tawfir.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "tawfir")
public record TawfirProperties(Jwt jwt, Otp otp, Payment payment, Cors cors) {

	public record Jwt(String signingKey, int accessTtlMinutes, int refreshTtlDays) {
	}

	public record Otp(String provider) {
	}

	public record Payment(String provider, String webhookSecret) {
	}

	public record Cors(List<String> allowedOrigins) {
	}

}
