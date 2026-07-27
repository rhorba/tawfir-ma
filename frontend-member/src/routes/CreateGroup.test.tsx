import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CreateGroup from './CreateGroup'

function renderCreateGroup() {
  return render(
    <MemoryRouter initialEntries={['/groups/new']}>
      <Routes>
        <Route path="/groups/new" element={<CreateGroup />} />
        <Route path="/groups/:groupId" element={<div>Group detail page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function fillRequiredFields() {
  fireEvent.change(screen.getByLabelText(/group name/i), { target: { value: 'Daret Bureau' } })
  fireEvent.change(screen.getByLabelText(/contribution amount/i), { target: { value: '500' } })
  fireEvent.change(screen.getByLabelText(/number of cycles/i), { target: { value: '2' } })
  fireEvent.change(screen.getByLabelText(/member 1 phone number/i), { target: { value: '+212612345678' } })
}

describe('CreateGroup', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('updates the name field on input', () => {
    renderCreateGroup()
    const nameInput = screen.getByLabelText(/group name/i) as HTMLInputElement

    fireEvent.change(nameInput, { target: { value: 'Daret Bureau' } })

    expect(nameInput.value).toBe('Daret Bureau')
  })

  it('updates the frequency field on select', () => {
    renderCreateGroup()
    const frequencySelect = screen.getByLabelText(/frequency/i) as HTMLSelectElement

    fireEvent.change(frequencySelect, { target: { value: 'WEEKLY' } })

    expect(frequencySelect.value).toBe('WEEKLY')
  })

  it('adds and removes a member row', () => {
    renderCreateGroup()

    fireEvent.click(screen.getByRole('button', { name: /add member/i }))
    expect(screen.getByLabelText(/member 2 phone number/i)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /remove member 2/i }))
    expect(screen.queryByLabelText(/member 2 phone number/i)).not.toBeInTheDocument()
  })

  it('creates the group and navigates to the group detail page', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      jsonResponse(201, {
        id: 'group-1',
        name: 'Daret Bureau',
        organizerId: 'user-1',
        contributionAmount: 500,
        currency: 'MAD',
        frequency: 'MONTHLY',
        totalCycles: 2,
        payoutOrderMode: 'RANDOMIZED',
        status: 'DRAFT',
        members: [],
      }),
    )
    renderCreateGroup()

    fillRequiredFields()
    fireEvent.click(screen.getByRole('button', { name: /continue/i }))

    await screen.findByText(/group detail page/i)
  })

  it('shows an error when creation fails', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      jsonResponse(400, {
        status: 400,
        error: 'Bad Request',
        message: 'members list contains a duplicate phone number',
        timestamp: new Date().toISOString(),
      }),
    )
    renderCreateGroup()

    fillRequiredFields()
    fireEvent.click(screen.getByRole('button', { name: /continue/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/duplicate phone number/i)
    expect(screen.getByRole('heading', { name: /create a group/i })).toBeInTheDocument()
  })
})
