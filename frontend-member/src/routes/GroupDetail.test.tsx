import { render, screen, fireEvent } from '@testing-library/react'
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

describe('GroupDetail', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(200, groupPayload())))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows the group id and defaults to the schedule tab', () => {
    renderGroupDetail('g-1')
    expect(screen.getByText('Group g-1')).toBeInTheDocument()
    expect(screen.getByText('No activity yet.')).toBeInTheDocument()
  })

  it('switches to the disputes tab', () => {
    renderGroupDetail()
    fireEvent.click(screen.getByRole('button', { name: /disputes/i }))
    expect(screen.getByText('No open disputes.')).toBeInTheDocument()
  })

  it('switches to the ledger tab', () => {
    renderGroupDetail()
    fireEvent.click(screen.getByRole('button', { name: /ledger/i }))
    expect(screen.getAllByText('No activity yet.')).toHaveLength(1)
  })

  it('loads group data and shows the member roster', async () => {
    renderGroupDetail()
    expect(await screen.findByText('Daret Bureau')).toBeInTheDocument()
    expect(screen.getByText(/\+212600000002 — MEMBER/)).toBeInTheDocument()
  })

  it('shows a Finalize button for the organizer on a DRAFT group', async () => {
    localStorage.setItem('tawfir_access_token', fakeJwt('organizer-1'))
    renderGroupDetail()

    expect(await screen.findByRole('button', { name: /finalize group/i })).toBeInTheDocument()
  })

  it('hides the Finalize button for a non-organizer member', async () => {
    localStorage.setItem('tawfir_access_token', fakeJwt('member-1'))
    renderGroupDetail()

    await screen.findByText('Daret Bureau')
    expect(screen.queryByRole('button', { name: /finalize group/i })).not.toBeInTheDocument()
  })

  it('finalizing updates the group status', async () => {
    localStorage.setItem('tawfir_access_token', fakeJwt('organizer-1'))
    vi.mocked(fetch)
      .mockResolvedValueOnce(jsonResponse(200, groupPayload()))
      .mockResolvedValueOnce(jsonResponse(200, groupPayload({ status: 'ACTIVE' })))
    renderGroupDetail()

    fireEvent.click(await screen.findByRole('button', { name: /finalize group/i }))

    expect(await screen.findByText(/ACTIVE/)).toBeInTheDocument()
  })
})
