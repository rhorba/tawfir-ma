import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it } from 'vitest'
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

describe('GroupDetail', () => {
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
})
