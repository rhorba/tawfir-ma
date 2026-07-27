import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../lib/apiClient'
import { requestOtp, verifyOtp } from '../lib/authClient'

type Step = 'phone' | 'otp'

export default function Login() {
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>('phone')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleRequestOtp(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setIsSubmitting(true)
    try {
      await requestOtp(phoneNumber)
      setStep('otp')
    } catch (err) {
      setError(errorMessage(err, 'Could not send the code. Please try again.'))
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleVerifyOtp(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setIsSubmitting(true)
    try {
      await verifyOtp(phoneNumber, code)
      navigate('/groups', { replace: true })
    } catch (err) {
      setError(errorMessage(err, 'Could not verify the code. Please try again.'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-(--color-background) px-4">
      <div className="w-full max-w-sm rounded-lg border border-(--color-border) p-6">
        <h1 className="mb-6 text-xl font-semibold text-(--color-text)">Tawfir.ma</h1>

        {error && (
          <p role="alert" className="mb-4 text-sm text-(--color-error, #dc2626)">
            {error}
          </p>
        )}

        {step === 'phone' ? (
          <form onSubmit={handleRequestOtp} className="space-y-4">
            <label className="block text-sm text-(--color-text-muted)" htmlFor="phone">
              Phone number
            </label>
            <input
              id="phone"
              type="tel"
              required
              value={phoneNumber}
              onChange={(e) => setPhoneNumber(e.target.value)}
              placeholder="+212 6XX XXX XXX"
              className="w-full rounded-md border border-(--color-border) px-3 py-2"
            />
            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full rounded-md bg-(--color-primary) px-3 py-2 text-white hover:bg-(--color-primary-dark) disabled:opacity-60"
            >
              {isSubmitting ? 'Sending…' : 'Send code'}
            </button>
          </form>
        ) : (
          <form onSubmit={handleVerifyOtp} className="space-y-4">
            <label className="block text-sm text-(--color-text-muted)" htmlFor="code">
              Enter the code sent to {phoneNumber}
            </label>
            <input
              id="code"
              type="text"
              inputMode="numeric"
              required
              value={code}
              onChange={(e) => setCode(e.target.value)}
              className="w-full rounded-md border border-(--color-border) px-3 py-2"
            />
            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full rounded-md bg-(--color-primary) px-3 py-2 text-white hover:bg-(--color-primary-dark) disabled:opacity-60"
            >
              {isSubmitting ? 'Verifying…' : 'Verify'}
            </button>
          </form>
        )}
      </div>
    </main>
  )
}

function errorMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiError && err.status === 429) {
    return 'Too many attempts. Please wait a few minutes and try again.'
  }
  if (err instanceof ApiError && err.message) {
    return err.message
  }
  return fallback
}
