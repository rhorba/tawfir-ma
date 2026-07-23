import { useState } from 'react'

export default function CreateGroup() {
  const [name, setName] = useState('')
  const [amount, setAmount] = useState('')
  const [frequency, setFrequency] = useState<'WEEKLY' | 'MONTHLY'>('MONTHLY')

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    // Wired to POST /api/v1/groups in Epic 2 (story 2.1)
  }

  return (
    <main className="mx-auto max-w-md px-4 py-8">
      <h1 className="mb-6 text-xl font-semibold text-(--color-text)">Create a group</h1>

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
            onChange={(e) => setFrequency(e.target.value as 'WEEKLY' | 'MONTHLY')}
            className="w-full rounded-md border border-(--color-border) px-3 py-2"
          >
            <option value="WEEKLY">Weekly</option>
            <option value="MONTHLY">Monthly</option>
          </select>
        </div>

        <button
          type="submit"
          className="w-full rounded-md bg-(--color-primary) px-3 py-2 text-white hover:bg-(--color-primary-dark)"
        >
          Continue
        </button>
      </form>
    </main>
  )
}
