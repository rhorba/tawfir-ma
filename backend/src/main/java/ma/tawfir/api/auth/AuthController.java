package ma.tawfir.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import ma.tawfir.api.auth.dto.LogoutRequest;
import ma.tawfir.api.auth.dto.MfaLoginVerifyRequest;
import ma.tawfir.api.auth.dto.OtpRequestRequest;
import ma.tawfir.api.auth.dto.OtpVerifyRequest;
import ma.tawfir.api.auth.dto.OtpVerifyResult;
import ma.tawfir.api.auth.dto.RefreshRequest;
import ma.tawfir.api.auth.dto.TokenResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/otp/request")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void requestOtp(@Valid @RequestBody OtpRequestRequest request, HttpServletRequest servletRequest) {
		authService.requestOtp(request.phoneNumber(), servletRequest.getRemoteAddr());
	}

	@PostMapping("/otp/verify")
	public OtpVerifyResult verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
		return authService.verifyOtp(request.phoneNumber(), request.code());
	}

	@PostMapping("/mfa/verify")
	public TokenResponse verifyMfa(@Valid @RequestBody MfaLoginVerifyRequest request) {
		return authService.verifyMfaLogin(request.mfaPendingToken(), request.code());
	}

	@PostMapping("/refresh")
	public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return authService.refresh(request.refreshToken());
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@Valid @RequestBody LogoutRequest request) {
		authService.logout(request.refreshToken());
	}

}
