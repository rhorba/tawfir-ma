import { render, screen, fireEvent } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import Login from './Login'

function renderLogin() {
  return render(
    <BrowserRouter>
      <Login />
    </BrowserRouter>,
  )
}

describe('Login', () => {
  it('starts on the phone entry step', () => {
    renderLogin()
    expect(screen.getByLabelText(/phone number/i)).toBeInTheDocument()
  })

  it('moves to the OTP step after requesting a code', () => {
    renderLogin()

    fireEvent.change(screen.getByLabelText(/phone number/i), {
      target: { value: '+212612345678' },
    })
    fireEvent.click(screen.getByRole('button', { name: /send code/i }))

    expect(screen.getByText(/enter the code sent to \+212612345678/i)).toBeInTheDocument()
  })

  it('submits the OTP step without throwing', () => {
    renderLogin()

    fireEvent.change(screen.getByLabelText(/phone number/i), {
      target: { value: '+212612345678' },
    })
    fireEvent.click(screen.getByRole('button', { name: /send code/i }))

    fireEvent.change(screen.getByLabelText(/enter the code sent to/i), {
      target: { value: '123456' },
    })
    fireEvent.click(screen.getByRole('button', { name: /verify/i }))

    expect(screen.getByRole('button', { name: /verify/i })).toBeInTheDocument()
  })
})
