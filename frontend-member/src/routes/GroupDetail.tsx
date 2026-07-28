import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { ApiError } from '../lib/apiClient'
import { getCurrentUserId } from '../lib/authClient'
import {
  confirmContribution,
  listContributions,
  markPaid,
  type Contribution,
} from '../lib/contributionsClient'
import { openDispute, listDisputes, resolveDispute, type Dispute } from '../lib/disputesClient'
import { finalizeGroup, getGroup, type GroupDetail as GroupDetailData, type Member } from '../lib/groupsClient'
import { listLedger, type LedgerEntry } from '../lib/ledgerClient'

type Tab = 'schedule' | 'ledger' | 'disputes'

function memberLabel(members: Member[], userId: string, currentUserId: string | null): string {
  if (userId === currentUserId) return 'You'
  return members.find((m) => m.userId === userId)?.phoneNumber ?? userId
}

export default function GroupDetail() {
  const { groupId } = useParams()
  const currentUserId = getCurrentUserId()
  const [tab, setTab] = useState<Tab>('schedule')
  const [group, setGroup] = useState<GroupDetailData | null>(null)
  const [contributions, setContributions] = useState<Contribution[]>([])
  const [ledgerEntries, setLedgerEntries] = useState<LedgerEntry[]>([])
  const [disputes, setDisputes] = useState<Dispute[]>([])
  const [error, setError] = useState<string | null>(null)
  const [isFinalizing, setIsFinalizing] = useState(false)
  const [isActing, setIsActing] = useState(false)
  const [disputeDraftFor, setDisputeDraftFor] = useState<string | null>(null)
  const [disputeReason, setDisputeReason] = useState('')
  const [resolutionReasonDraft, setResolutionReasonDraft] = useState<Record<string, string>>({})

  const loadAll = useCallback(() => {
    if (!groupId) return
    getGroup(groupId)
      .then(setGroup)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this group.'))
    listContributions(groupId).then(setContributions).catch(() => {})
    listLedger(groupId).then(setLedgerEntries).catch(() => {})
    listDisputes(groupId).then(setDisputes).catch(() => {})
  }, [groupId])

  useEffect(() => {
    loadAll()
  }, [loadAll])

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

  async function handleMarkPaid(scheduleId: string) {
    if (!groupId) return
    setError(null)
    setIsActing(true)
    try {
      await markPaid(groupId, scheduleId)
      loadAll()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not mark this contribution as paid.')
    } finally {
      setIsActing(false)
    }
  }

  async function handleConfirm(scheduleId: string) {
    if (!groupId) return
    setError(null)
    setIsActing(true)
    try {
      await confirmContribution(groupId, scheduleId)
      loadAll()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not confirm this contribution.')
    } finally {
      setIsActing(false)
    }
  }

  async function handleOpenDispute(ledgerEntryId: string) {
    if (!groupId || !disputeReason.trim()) return
    setError(null)
    setIsActing(true)
    try {
      await openDispute(groupId, { ledgerEntryId, reason: disputeReason.trim() })
      setDisputeDraftFor(null)
      setDisputeReason('')
      loadAll()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not open a dispute for this contribution.')
    } finally {
      setIsActing(false)
    }
  }

  async function handleResolve(disputeId: string, resolution: 'ACCEPTED' | 'REJECTED') {
    const resolutionReason = (resolutionReasonDraft[disputeId] ?? '').trim()
    if (!resolutionReason) return
    setError(null)
    setIsActing(true)
    try {
      await resolveDispute(disputeId, { resolution, resolutionReason })
      loadAll()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not resolve this dispute.')
    } finally {
      setIsActing(false)
    }
  }

  const isOrganizer = group !== null && group.organizerId === currentUserId

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
        {tab === 'schedule' &&
          (contributions.length === 0 ? (
            <p>No activity yet.</p>
          ) : (
            <ul className="space-y-3">
              {contributions.map((contribution) => {
                const ledgerEntry = ledgerEntries.find((e) => e.contributionScheduleId === contribution.id)
                return (
                  <li key={contribution.id} className="rounded-md border border-(--color-border) p-3 text-sm">
                    <p>
                      Cycle {contribution.cycleNumber} · {memberLabel(group?.members ?? [], contribution.userId, currentUserId)} ·{' '}
                      {contribution.dueDate} · <span className="font-medium">{contribution.status}</span>
                    </p>
                    <div className="mt-2 flex flex-wrap gap-2">
                      {contribution.userId === currentUserId &&
                        (contribution.status === 'PENDING' || contribution.status === 'LATE') && (
                          <button
                            onClick={() => handleMarkPaid(contribution.id)}
                            disabled={isActing}
                            className="rounded-md bg-(--color-primary) px-2 py-1 text-xs text-white disabled:opacity-60"
                          >
                            Mark paid
                          </button>
                        )}
                      {isOrganizer && contribution.status === 'MARKED_PAID' && (
                        <button
                          onClick={() => handleConfirm(contribution.id)}
                          disabled={isActing}
                          className="rounded-md bg-(--color-primary) px-2 py-1 text-xs text-white disabled:opacity-60"
                        >
                          Confirm
                        </button>
                      )}
                      {contribution.status === 'CONFIRMED' && ledgerEntry && (
                        <button
                          onClick={() => setDisputeDraftFor(contribution.id)}
                          disabled={isActing}
                          className="rounded-md border border-(--color-border) px-2 py-1 text-xs text-(--color-text-muted) disabled:opacity-60"
                        >
                          Dispute
                        </button>
                      )}
                    </div>
                    {disputeDraftFor === contribution.id && ledgerEntry && (
                      <div className="mt-2 flex gap-2">
                        <input
                          aria-label={`Dispute reason for cycle ${contribution.cycleNumber}`}
                          value={disputeReason}
                          onChange={(e) => setDisputeReason(e.target.value)}
                          placeholder="Why are you disputing this?"
                          className="w-full rounded-md border border-(--color-border) px-2 py-1 text-xs"
                        />
                        <button
                          onClick={() => handleOpenDispute(ledgerEntry.id)}
                          disabled={isActing || !disputeReason.trim()}
                          className="rounded-md bg-(--color-primary) px-2 py-1 text-xs text-white disabled:opacity-60"
                        >
                          Submit
                        </button>
                      </div>
                    )}
                  </li>
                )
              })}
            </ul>
          ))}

        {tab === 'ledger' &&
          (ledgerEntries.length === 0 ? (
            <p>No activity yet.</p>
          ) : (
            <ul className="space-y-2 text-sm">
              {ledgerEntries.map((entry) => (
                <li key={entry.id} className="rounded-md border border-(--color-border) p-3">
                  {entry.entryType} · {entry.amount} {group?.currency} · {entry.source}
                  {entry.reversalOfEntryId ? ' (correction)' : ''}
                </li>
              ))}
            </ul>
          ))}

        {tab === 'disputes' &&
          (disputes.length === 0 ? (
            <p>No open disputes.</p>
          ) : (
            <ul className="space-y-3 text-sm">
              {disputes.map((dispute) => (
                <li key={dispute.id} className="rounded-md border border-(--color-border) p-3">
                  <p>
                    {memberLabel(group?.members ?? [], dispute.raisedByUserId, currentUserId)}: {dispute.reason} ·{' '}
                    <span className="font-medium">{dispute.status}</span>
                  </p>
                  {dispute.status !== 'OPEN' && dispute.resolutionReason && (
                    <p className="mt-1 text-xs">Resolution: {dispute.resolutionReason}</p>
                  )}
                  {dispute.status === 'OPEN' && isOrganizer && (
                    <div className="mt-2 flex gap-2">
                      <input
                        aria-label={`Resolution reason for dispute ${dispute.id}`}
                        value={resolutionReasonDraft[dispute.id] ?? ''}
                        onChange={(e) =>
                          setResolutionReasonDraft((current) => ({ ...current, [dispute.id]: e.target.value }))
                        }
                        placeholder="Resolution reason"
                        className="w-full rounded-md border border-(--color-border) px-2 py-1 text-xs"
                      />
                      <button
                        onClick={() => handleResolve(dispute.id, 'ACCEPTED')}
                        disabled={isActing || !(resolutionReasonDraft[dispute.id] ?? '').trim()}
                        className="rounded-md bg-(--color-primary) px-2 py-1 text-xs text-white disabled:opacity-60"
                      >
                        Accept
                      </button>
                      <button
                        onClick={() => handleResolve(dispute.id, 'REJECTED')}
                        disabled={isActing || !(resolutionReasonDraft[dispute.id] ?? '').trim()}
                        className="rounded-md border border-(--color-border) px-2 py-1 text-xs text-(--color-text-muted) disabled:opacity-60"
                      >
                        Reject
                      </button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          ))}
      </div>
    </main>
  )
}
