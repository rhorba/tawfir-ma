import { useState } from 'react'

type Step = 'phone' | 'otp'

export default function Login() {
  const [step, setStep] = useState<Step>('phone')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [code, setCode] = useState('')

  function handleRequestOtp(e: React.FormEvent) {
    e.preventDefault()
    // Wired to POST /api/v1/auth/otp/request in Epic 1 (story 1.1)
    setStep('otp')
  }

  function handleVerifyOtp(e: React.FormEvent) {
    e.preventDefault()
    // Wired to POST /api/v1/auth/otp/verify in Epic 1 (story 1.2)
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-(--color-background) px-4">
      <div className="w-full max-w-sm rounded-lg border border-(--color-border) p-6">
        <h1 className="mb-6 text-xl font-semibold text-(--color-text)">Tawfir.ma</h1>

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
              className="w-full rounded-md bg-(--color-primary) px-3 py-2 text-white hover:bg-(--color-primary-dark)"
            >
              Send code
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
              className="w-full rounded-md bg-(--color-primary) px-3 py-2 text-white hover:bg-(--color-primary-dark)"
            >
              Verify
            </button>
          </form>
        )}
      </div>
    </main>
  )
}
