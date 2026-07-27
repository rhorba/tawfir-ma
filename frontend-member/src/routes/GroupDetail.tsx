import { useState } from 'react'
import { useParams } from 'react-router'

type Tab = 'schedule' | 'ledger' | 'disputes'

export default function GroupDetail() {
  const { groupId } = useParams()
  const [tab, setTab] = useState<Tab>('schedule')

  // Wired to GET /api/v1/groups/:id, /contributions, /ledger, /disputes in Epic 2/3/5

  return (
    <main className="mx-auto max-w-2xl px-4 py-8">
      <h1 className="text-xl font-semibold text-(--color-text)">Group {groupId}</h1>

      <nav className="mt-4 flex gap-4 border-b border-(--color-border)">
        {(['schedule', 'ledger', 'disputes'] as const).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`pb-2 text-sm capitalize ${
              tab === t
                ? 'border-b-2 border-(--color-primary) text-(--color-primary)'
                : 'text-(--color-text-muted)'
            }`}
          >
            {t}
          </button>
        ))}
      </nav>

      <div className="mt-4 text-(--color-text-muted)">
        {tab === 'schedule' && <p>No activity yet.</p>}
        {tab === 'ledger' && <p>No activity yet.</p>}
        {tab === 'disputes' && <p>No open disputes.</p>}
      </div>
    </main>
  )
}
