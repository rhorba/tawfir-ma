package ma.tawfir.api.webhook;

public class InvalidWebhookSignatureException extends RuntimeException {

	public InvalidWebhookSignatureException(String message) {
		super(message);
	}

}
