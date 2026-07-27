import { apiFetch } from './apiClient'

export const ACCESS_TOKEN_KEY = 'tawfir_access_token'
export const REFRESH_TOKEN_KEY = 'tawfir_refresh_token'

interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresInSeconds: number
}

export function requestOtp(phoneNumber: string): Promise<void> {
  return apiFetch<void>('/api/v1/auth/otp/request', {
    method: 'POST',
    body: JSON.stringify({ phoneNumber }),
  })
}

export async function verifyOtp(phoneNumber: string, code: string): Promise<TokenResponse> {
  const tokens = await apiFetch<TokenResponse>('/api/v1/auth/otp/verify', {
    method: 'POST',
    body: JSON.stringify({ phoneNumber, code }),
  })
  storeTokens(tokens)
  return tokens
}

export function storeTokens(tokens: TokenResponse): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}
