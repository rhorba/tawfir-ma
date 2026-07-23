import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import CreateGroup from './CreateGroup'

describe('CreateGroup', () => {
  it('updates the name field on input', () => {
    render(<CreateGroup />)
    const nameInput = screen.getByLabelText(/group name/i) as HTMLInputElement

    fireEvent.change(nameInput, { target: { value: 'Daret Bureau' } })

    expect(nameInput.value).toBe('Daret Bureau')
  })

  it('updates the frequency field on select', () => {
    render(<CreateGroup />)
    const frequencySelect = screen.getByLabelText(/frequency/i) as HTMLSelectElement

    fireEvent.change(frequencySelect, { target: { value: 'WEEKLY' } })

    expect(frequencySelect.value).toBe('WEEKLY')
  })

  it('submits without throwing once required fields are filled', () => {
    render(<CreateGroup />)

    fireEvent.change(screen.getByLabelText(/group name/i), { target: { value: 'Daret Bureau' } })
    fireEvent.change(screen.getByLabelText(/contribution amount/i), { target: { value: '500' } })
    fireEvent.click(screen.getByRole('button', { name: /continue/i }))

    expect(screen.getByRole('heading', { name: /create a group/i })).toBeInTheDocument()
  })
})
