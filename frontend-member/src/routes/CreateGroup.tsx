import { useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '../lib/apiClient'
import { createGroup, type Frequency, type PayoutOrderMode } from '../lib/groupsClient'

export default function CreateGroup() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [amount, setAmount] = useState('')
  const [frequency, setFrequency] = useState<Frequency>('MONTHLY')
  const [totalCycles, setTotalCycles] = useState('')
  const [payoutOrderMode, setPayoutOrderMode] = useState<PayoutOrderMode>('RANDOMIZED')
  const [members, setMembers] = useState([''])
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  function updateMember(index: number, value: string) {
    setMembers((current) => current.map((m, i) => (i === index ? value : m)))
  }

  function addMember() {
    setMembers((current) => [...current, ''])
  }

  function removeMember(index: number) {
    setMembers((current) => current.filter((_, i) => i !== index))
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setIsSubmitting(true)
    try {
      const group = await createGroup({
        name,
        contributionAmount: Number(amount),
        frequency,
        totalCycles: Number(totalCycles),
        payoutOrderMode,
        members: members.map((m) => m.trim()).filter(Boolean),
      })
      navigate(`/groups/${group.id}`, { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create the group. Please try again.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="mx-auto max-w-md px-4 py-8">
      <h1 className="mb-6 text-xl font-semibold text-(--color-text)">Create a group</h1>

      {error && (
        <p role="alert" className="mb-4 text-sm text-(--color-error, #dc2626)">
          {error}
        </p>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm text-(--color-text-muted)" htmlFor="name">
            Group name
          </label>
          <input
            id="name"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full rounded-md border border-(--color-border) px-3 py-2"
          />
        </div>

        <div>
          <label className="block text-sm text-(--color-text-muted)" htmlFor="amount">
            Contribution amount (MAD)
          </label>
          <input
            id="amount"
            type="number"
            min="1"
            step="0.01"
            required
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            className="w-full rounded-md border border-(--color-border) px-3 py-2"
          />
        </div>

        <div>
          <label className="block text-sm text-(--color-text-muted)" htmlFor="frequency">
            Frequency
          </label>
          <select
            id="frequency"
            value={frequency}
            onChange={(e) => setFrequency(e.target.value as Frequency)}
            className="w-full rounded-md border border-(--color-border) px-3 py-2"
          >
            <option value="WEEKLY">Weekly</option>
            <option value="MONTHLY">Monthly</option>
          </select>
        </div>

        <div>
          <label className="block text-sm text-(--color-text-muted)" htmlFor="totalCycles">
            Number of cycles
          </label>
          <input
            id="totalCycles"
            type="number"
            min="1"
            step="1"
            required
            value={totalCycles}
            onChange={(e) => setTotalCycles(e.target.value)}
            className="w-full rounded-md border border-(--color-border) px-3 py-2"
          />
          <p className="mt-1 text-xs text-(--color-text-muted)">Must equal the number of members below.</p>
        </div>

        <div>
          <label className="block text-sm text-(--color-text-muted)" htmlFor="payoutOrderMode">
            Payout order
          </label>
          <select
            id="payoutOrderMode"
            value={payoutOrderMode}
            onChange={(e) => setPayoutOrderMode(e.target.value as PayoutOrderMode)}
            className="w-full rounded-md border border-(--color-border) px-3 py-2"
          >
            <option value="RANDOMIZED">Randomize</option>
            <option value="MANUAL">Manual (order below)</option>
          </select>
        </div>

        <div>
          <span className="block text-sm text-(--color-text-muted)">Members (include your own number)</span>
          <div className="mt-1 space-y-2">
            {members.map((member, index) => (
              <div key={index} className="flex gap-2">
                <input
                  aria-label={`Member ${index + 1} phone number`}
                  type="tel"
                  required
                  value={member}
                  onChange={(e) => updateMember(index, e.target.value)}
                  placeholder="+212 6XX XXX XXX"
                  className="w-full rounded-md border border-(--color-border) px-3 py-2"
                />
                {members.length > 1 && (
                  <button
                    type="button"
                    onClick={() => removeMember(index)}
                    aria-label={`Remove member ${index + 1}`}
                    className="rounded-md border border-(--color-border) px-3 text-(--color-text-muted)"
                  >
                    &times;
                  </button>
                )}
              </div>
            ))}
          </div>
          <button
            type="button"
            onClick={addMember}
            className="mt-2 text-sm text-(--color-primary)"
          >
            + Add member
          </button>
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full rounded-md bg-(--color-primary) px-3 py-2 text-white hover:bg-(--color-primary-dark) disabled:opacity-60"
        >
          {isSubmitting ? 'Creating…' : 'Continue'}
        </button>
      </form>
    </main>
  )
}
