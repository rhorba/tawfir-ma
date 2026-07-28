import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import GroupDetail from './GroupDetail'

function renderGroupDetail(groupId = 'g-1') {
  return render(
    <MemoryRouter initialEntries={[`/groups/${groupId}`]}>
      <Routes>
        <Route path="/groups/:groupId" element={<GroupDetail />} />
      </Routes>
    </MemoryRouter>,
  )
}

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function fakeJwt(sub: string): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const payload = btoa(JSON.stringify({ sub }))
  return `${header}.${payload}.signature`
}

function groupPayload(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: 'g-1',
    name: 'Daret Bureau',
    organizerId: 'organizer-1',
    contributionAmount: 500,
    currency: 'MAD',
    frequency: 'MONTHLY',
    totalCycles: 2,
    payoutOrderMode: 'MANUAL',
    status: 'DRAFT',
    members: [
      { userId: 'organizer-1', phoneNumber: '+212600000001', roleInGroup: 'ORGANIZER', payoutPosition: 1 },
      { userId: 'member-1', phoneNumber: '+212600000002', roleInGroup: 'MEMBER', payoutPosition: 2 },
    ],
    ...overrides,
  }
}

function requestUrl(input: string | URL | Request): string {
  if (typeof input === 'string') return input
  if (input instanceof URL) return input.toString()
  return input.url
}

/** Routes fetch calls to canned responses by path, and lets a test override any of the four endpoints. */
function stubFetch(options: {
  group?: unknown
  contributions?: unknown[]
  ledger?: unknown[]
  disputes?: unknown[]
  onAction?: (url: string, init?: RequestInit) => Response | undefined
}) {
  let currentGroup = options.group ?? groupPayload()
  const fetchMock = vi.fn((input: string | URL | Request, init?: RequestInit) => {
    const url = requestUrl(input)
    const overridden = options.onAction?.(url, init)
    if (overridden) return Promise.resolve(overridden)
    if (url.endsWith('/finalize')) {
      currentGroup = { ...(currentGroup as Record<string, unknown>), status: 'ACTIVE' }
      return Promise.resolve(jsonResponse(200, currentGroup))
    }
    if (url.includes('/contributions')) return Promise.resolve(jsonResponse(200, options.contributions ?? []))
    if (url.includes('/ledger')) return Promise.resolve(jsonResponse(200, options.ledger ?? []))
    if (url.includes('/disputes')) return Promise.resolve(jsonResponse(200, options.disputes ?? []))
    return Promise.resolve(jsonResponse(200, currentGroup))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('GroupDetail', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows the group id and defaults to the schedule tab', () => {
    stubFetch({})
    renderGroupDetail('g-1')
    expect(screen.getByText('Group g-1')).toBeInTheDocument()
    expect(screen.getByText('No activity yet.')).toBeInTheDocument()
  })

  it('switches to the disputes tab', () => {
    stubFetch({})
    renderGroupDetail()
    fireEvent.click(screen.getByRole('button', { name: /disputes/i }))
    expect(screen.getByText('No open disputes.')).toBeInTheDocument()
  })

  it('switches to the ledger tab', () => {
    stubFetch({})
    renderGroupDetail()
    fireEvent.click(screen.getByRole('button', { name: /ledger/i }))
    expect(screen.getAllByText('No activity yet.')).toHaveLength(1)
  })

  it('loads group data and shows the member roster', async () => {
    stubFetch({})
    renderGroupDetail()
    expect(await screen.findByText('Daret Bureau')).toBeInTheDocument()
    expect(screen.getByText(/\+212600000002 — MEMBER/)).toBeInTheDocument()
  })

  it('shows a Finalize button for the organizer on a DRAFT group', async () => {
    localStorage.setItem('tawfir_access_token', fakeJwt('organizer-1'))
    stubFetch({})
    renderGroupDetail()

    expect(await screen.findByRole('button', { name: /finalize group/i })).toBeInTheDocument()
  })

  it('hides the Finalize button for a non-organizer member', async () => {
    localStorage.setItem('tawfir_access_token', fakeJwt('member-1'))
    stubFetch({})
    renderGroupDetail()

    await screen.findByText('Daret Bureau')
    expect(screen.queryByRole('button', { name: /finalize group/i })).not.toBeInTheDocument()
  })

  it('finalizing updates the group status', async () => {
    localStorage.setItem('tawfir_access_token', fakeJwt('organizer-1'))
    stubFetch({})
    renderGroupDetail()

    fireEvent.click(await screen.findByRole('button', { name: /finalize group/i }))

    expect(await screen.findByText(/ACTIVE/)).toBeInTheDocument()
  })

  it('shows a Mark paid button for the member on their own PENDING contribution', async () => {
    localStorage.setItem('tawfir_access_token', fakeJwt('member-1'))
    stubFetch({
      contributions: [
        { id: 'sched-1', groupId: 'g-1', cycleNumber: 1, userId: 'member-1', dueDate: '2026-08-01', status: 'PENDING' },
      ],
    })
    renderGroupDetail()

    expect(await screen.findByRole('button', { name: /mark paid/i })).toBeInTheDocument()
  })

  it('does not show Mark paid for another member\'s contribution', async () => {
    localStorage.setItem('tawfir_access_token', fakeJwt('member-1'))
    stubFetch({
      contributions: [
        { id: 'sched-1', groupId: 'g-1', cycleNumber: 1, userId: 'organizer-1', dueDate: '2026-08-01', status: 'PENDING' },
      ],
    })
    renderGroupDetail()

    await screen.findByText(/Cycle 1/)
    expect(screen.queryByRole('button', { name: /mark paid/i })).not.toBeInTheDocument()
  })

  it('marking paid calls the API and refreshes the schedule', async () => {
    localStorage.setItem('tawfir_access_token', fakeJwt('member-1'))
    let marked = false
    const fetchMock = stubFetch({
      contributions: [
        { id: 'sched-1', groupId: 'g-1', cycleNumber: 1, userId: 'member-1', dueDate: '2026-08-01', status: 'PENDING' },
      ],
      onAction: (url) => {
        if (url.includes('/mark-paid')) {
          marked = true
          return jsonResponse(200, {
            id: 'sched-1', groupId: 'g-1', cycleNumber: 1, userId: 'member-1', dueDate: '2026-08-01', status: 'MARKED_PAID',
          })
        }
        if (marked && url.includes('/contributions')) {
          return jsonResponse(200, [
            { id: 'sched-1', groupId: 'g-1', cycleNumber: 1, userId: 'member-1', dueDate: '2026-08-01', status: 'MARKED_PAID' },
          ])
        }
        return undefined
      },
    })
    renderGroupDetail()

    fireEvent.click(await screen.findByRole('button', { name: /mark paid/i }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/mark-paid'), expect.anything()))
    expect(await screen.findByText(/MARKED_PAID/)).toBeInTheDocument()
  })

  it('shows a Confirm button for the organizer on a MARKED_PAID contribution', async () => {
    localStorage.setItem('tawfir_access_token', fakeJwt('organizer-1'))
    stubFetch({
      contributions: [
        { id: 'sched-1', groupId: 'g-1', cycleNumber: 1, userId: 'member-1', dueDate: '2026-08-01', status: 'MARKED_PAID' },
      ],
    })
    renderGroupDetail()

    expect(await screen.findByRole('button', { name: /confirm/i })).toBeInTheDocument()
  })

  it('shows a Dispute button for a CONFIRMED contribution with a ledger entry', async () => {
    localStorage.setItem('tawfir_access_token', fakeJwt('member-1'))
    stubFetch({
      contributions: [
        { id: 'sched-1', groupId: 'g-1', cycleNumber: 1, userId: 'member-1', dueDate: '2026-08-01', status: 'CONFIRMED' },
      ],
      ledger: [
        {
          id: 'ledger-1', groupId: 'g-1', entryType: 'CONTRIBUTION', contributionScheduleId: 'sched-1',
          payoutScheduleId: null, actorUserId: 'organizer-1', amount: 500, source: 'ORGANIZER_CONFIRMED',
          reversalOfEntryId: null, createdAt: '2026-08-01T00:00:00Z',
        },
      ],
    })
    renderGroupDetail()

    expect(await screen.findByRole('button', { name: 'Dispute' })).toBeInTheDocument()
  })

  it('submitting a dispute calls the API with the ledger entry id', async () => {
    localStorage.setItem('tawfir_access_token', fakeJwt('member-1'))
    const fetchMock = stubFetch({
      contributions: [
        { id: 'sched-1', groupId: 'g-1', cycleNumber: 1, userId: 'member-1', dueDate: '2026-08-01', status: 'CONFIRMED' },
      ],
      ledger: [
        {
          id: 'ledger-1', groupId: 'g-1', entryType: 'CONTRIBUTION', contributionScheduleId: 'sched-1',
          payoutScheduleId: null, actorUserId: 'organizer-1', amount: 500, source: 'ORGANIZER_CONFIRMED',
          reversalOfEntryId: null, createdAt: '2026-08-01T00:00:00Z',
        },
      ],
      onAction: (url, init) => {
        if (url.endsWith('/groups/g-1/disputes') && init?.method === 'POST') {
          return jsonResponse(201, {
            id: 'dispute-1', groupId: 'g-1', ledgerEntryId: 'ledger-1', raisedByUserId: 'member-1',
            reason: 'wrong amount', evidenceNote: null, status: 'OPEN', resolvedByUserId: null,
            resolutionReason: null, createdAt: '2026-08-02T00:00:00Z', resolvedAt: null,
          })
        }
        return undefined
      },
    })
    renderGroupDetail()

    fireEvent.click(await screen.findByRole('button', { name: 'Dispute' }))
    fireEvent.change(screen.getByLabelText(/dispute reason/i), { target: { value: 'wrong amount' } })
    fireEvent.click(screen.getByRole('button', { name: /submit/i }))

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('/groups/g-1/disputes'),
        expect.objectContaining({ method: 'POST', body: JSON.stringify({ ledgerEntryId: 'ledger-1', reason: 'wrong amount' }) }),
      ),
    )
  })

  it('shows resolution controls for the organizer on an OPEN dispute', async () => {
    localStorage.setItem('tawfir_access_token', fakeJwt('organizer-1'))
    stubFetch({
      disputes: [
        {
          id: 'dispute-1', groupId: 'g-1', ledgerEntryId: 'ledger-1', raisedByUserId: 'member-1',
          reason: 'wrong amount', evidenceNote: null, status: 'OPEN', resolvedByUserId: null,
          resolutionReason: null, createdAt: '2026-08-02T00:00:00Z', resolvedAt: null,
        },
      ],
    })
    renderGroupDetail()
    fireEvent.click(screen.getByRole('button', { name: /disputes/i }))

    expect(await screen.findByRole('button', { name: /accept/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /reject/i })).toBeInTheDocument()
  })

  it('resolving a dispute calls the API with the resolution and reason', async () => {
    localStorage.setItem('tawfir_access_token', fakeJwt('organizer-1'))
    const fetchMock = stubFetch({
      disputes: [
        {
          id: 'dispute-1', groupId: 'g-1', ledgerEntryId: 'ledger-1', raisedByUserId: 'member-1',
          reason: 'wrong amount', evidenceNote: null, status: 'OPEN', resolvedByUserId: null,
          resolutionReason: null, createdAt: '2026-08-02T00:00:00Z', resolvedAt: null,
        },
      ],
      onAction: (url) => {
        if (url.endsWith('/disputes/dispute-1/resolve')) {
          return jsonResponse(200, {
            id: 'dispute-1', groupId: 'g-1', ledgerEntryId: 'ledger-1', raisedByUserId: 'member-1',
            reason: 'wrong amount', evidenceNote: null, status: 'ACCEPTED', resolvedByUserId: 'organizer-1',
            resolutionReason: 'confirmed error', createdAt: '2026-08-02T00:00:00Z', resolvedAt: '2026-08-03T00:00:00Z',
          })
        }
        return undefined
      },
    })
    renderGroupDetail()
    fireEvent.click(screen.getByRole('button', { name: /disputes/i }))
    fireEvent.change(await screen.findByLabelText(/resolution reason/i), { target: { value: 'confirmed error' } })
    fireEvent.click(screen.getByRole('button', { name: /accept/i }))

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('/disputes/dispute-1/resolve'),
        expect.objectContaining({
          method: 'PATCH',
          body: JSON.stringify({ resolution: 'ACCEPTED', resolutionReason: 'confirmed error' }),
        }),
      ),
    )
  })
})
