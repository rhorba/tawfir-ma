import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import GroupsList from './GroupsList'

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function renderGroupsList() {
  return render(
    <MemoryRouter>
      <GroupsList />
    </MemoryRouter>,
  )
}

describe('GroupsList', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows the empty state when the user has no groups', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(200, []))
    renderGroupsList()

    expect(await screen.findByText(/no groups yet/i)).toBeInTheDocument()
  })

  it('renders each group returned by the API', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      jsonResponse(200, [
        { id: 'g-1', name: 'Daret Bureau', contributionAmount: 500, currency: 'MAD', frequency: 'MONTHLY', totalCycles: 2, status: 'ACTIVE', memberCount: 2 },
      ]),
    )
    renderGroupsList()

    expect(await screen.findByText('Daret Bureau')).toBeInTheDocument()
    expect(screen.getByText('ACTIVE')).toBeInTheDocument()
  })

  it('shows an error when loading groups fails', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      jsonResponse(401, { status: 401, error: 'Unauthorized', message: 'Invalid token', timestamp: new Date().toISOString() }),
    )
    renderGroupsList()

    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid token/i)
  })
})
