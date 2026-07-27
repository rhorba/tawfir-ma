import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Login from './Login'

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/groups" element={<div>Groups page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

async function fillPhoneAndSend() {
  fireEvent.change(screen.getByLabelText(/phone number/i), {
    target: { value: '+212612345678' },
  })
  fireEvent.click(screen.getByRole('button', { name: /send code/i }))
  await screen.findByText(/enter the code sent to/i)
}

describe('Login', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('starts on the phone entry step', () => {
    renderLogin()
    expect(screen.getByLabelText(/phone number/i)).toBeInTheDocument()
  })

  it('moves to the OTP step after requesting a code', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(null, { status: 202 }))
    renderLogin()

    await fillPhoneAndSend()

    expect(screen.getByText(/enter the code sent to \+212612345678/i)).toBeInTheDocument()
  })

  it('shows a rate-limit message when otp/request is throttled', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      jsonResponse(429, { status: 429, error: 'Too Many Requests', message: 'Too many OTP requests', timestamp: new Date().toISOString() }),
    )
    renderLogin()

    fireEvent.change(screen.getByLabelText(/phone number/i), {
      target: { value: '+212612345678' },
    })
    fireEvent.click(screen.getByRole('button', { name: /send code/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/too many attempts/i)
  })

  it('verifies the OTP, stores tokens, and navigates to /groups', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(new Response(null, { status: 202 }))
      .mockResolvedValueOnce(
        jsonResponse(200, {
          accessToken: 'access-123',
          refreshToken: 'refresh-456',
          tokenType: 'Bearer',
          expiresInSeconds: 900,
        }),
      )
    renderLogin()

    await fillPhoneAndSend()

    fireEvent.change(screen.getByLabelText(/enter the code sent to/i), {
      target: { value: '123456' },
    })
    fireEvent.click(screen.getByRole('button', { name: /verify/i }))

    await screen.findByText(/groups page/i)
    expect(localStorage.getItem('tawfir_access_token')).toBe('access-123')
    expect(localStorage.getItem('tawfir_refresh_token')).toBe('refresh-456')
  })

  it('shows an error and stays on the OTP step when the code is invalid', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(new Response(null, { status: 202 }))
      .mockResolvedValueOnce(
        jsonResponse(401, { status: 401, error: 'Unauthorized', message: 'Invalid or expired code', timestamp: new Date().toISOString() }),
      )
    renderLogin()

    await fillPhoneAndSend()

    fireEvent.change(screen.getByLabelText(/enter the code sent to/i), {
      target: { value: '000000' },
    })
    fireEvent.click(screen.getByRole('button', { name: /verify/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid or expired code/i)
    await waitFor(() => expect(screen.getByRole('button', { name: /verify/i })).toBeInTheDocument())
    expect(localStorage.getItem('tawfir_access_token')).toBeNull()
  })
})
