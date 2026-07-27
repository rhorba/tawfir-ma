import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

export const ACCESS_TOKEN_KEY = 'tawfir_access_token';
export const REFRESH_TOKEN_KEY = 'tawfir_refresh_token';

interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export class AuthApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  async requestOtp(phoneNumber: string): Promise<void> {
    await this.post<void>('/api/v1/auth/otp/request', { phoneNumber });
  }

  async verifyOtp(phoneNumber: string, code: string): Promise<TokenResponse> {
    return this.post<TokenResponse>('/api/v1/auth/otp/verify', { phoneNumber, code });
  }

  storeTokens(tokens: TokenResponse): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  }

  clearTokens(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  }

  /** Decodes the JWT payload without verifying the signature — UX gating only, not a security boundary. */
  decodeRole(accessToken: string): string | null {
    try {
      const payload = accessToken.split('.')[1];
      const decoded = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
      return decoded.role ?? null;
    } catch {
      return null;
    }
  }

  private async post<T>(path: string, body: unknown): Promise<T> {
    try {
      return await firstValueFrom(this.http.post<T>(`${environment.apiBaseUrl}${path}`, body));
    } catch (err) {
      if (err instanceof HttpErrorResponse) {
        const message = (err.error as { message?: string } | null)?.message ?? 'Request failed';
        throw new AuthApiError(err.status, message);
      }
      throw err;
    }
  }
}
