import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { ApiError } from '../lib/apiClient'
import { getCurrentUserId } from '../lib/authClient'
import { finalizeGroup, getGroup, type GroupDetail as GroupDetailData } from '../lib/groupsClient'

type Tab = 'schedule' | 'ledger' | 'disputes'

export default function GroupDetail() {
  const { groupId } = useParams()
  const [tab, setTab] = useState<Tab>('schedule')
  const [group, setGroup] = useState<GroupDetailData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isFinalizing, setIsFinalizing] = useState(false)

  const loadGroup = useCallback(() => {
    if (!groupId) return
    getGroup(groupId)
      .then(setGroup)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this group.'))
  }, [groupId])

  useEffect(() => {
    loadGroup()
  }, [loadGroup])

  async function handleFinalize() {
    if (!groupId) return
    setError(null)
    setIsFinalizing(true)
    try {
      const updated = await finalizeGroup(groupId)
      setGroup(updated)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not finalize this group.')
    } finally {
      setIsFinalizing(false)
    }
  }

  const isOrganizer = group !== null && group.organizerId === getCurrentUserId()

  return (
    <main className="mx-auto max-w-2xl px-4 py-8">
      <h1 className="text-xl font-semibold text-(--color-text)">{group?.name ?? `Group ${groupId}`}</h1>

      {error && (
        <p role="alert" className="mt-2 text-sm text-(--color-error, #dc2626)">
          {error}
        </p>
      )}

      {group && (
        <div className="mt-2 text-sm text-(--color-text-muted)">
          <p>
            {group.contributionAmount} {group.currency} · {group.frequency.toLowerCase()} · {group.status}
          </p>
          <ul className="mt-2 space-y-1">
            {group.members.map((member) => (
              <li key={member.userId}>
                {member.phoneNumber} — {member.roleInGroup}
                {member.payoutPosition ? ` (payout #${member.payoutPosition})` : ''}
              </li>
            ))}
          </ul>
          {group.status === 'DRAFT' && isOrganizer && (
            <button
              onClick={handleFinalize}
              disabled={isFinalizing}
              className="mt-4 rounded-md bg-(--color-primary) px-3 py-2 text-sm text-white hover:bg-(--color-primary-dark) disabled:opacity-60"
            >
              {isFinalizing ? 'Finalizing…' : 'Finalize group'}
            </button>
          )}
        </div>
      )}

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
