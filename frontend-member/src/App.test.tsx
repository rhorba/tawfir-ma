import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('App routing', () => {
  it('redirects the root path to /groups', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByText(/my groups/i)).toBeInTheDocument()
  })

  it('renders the create-group form at /groups/new', () => {
    render(
      <MemoryRouter initialEntries={['/groups/new']}>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByText(/create a group/i)).toBeInTheDocument()
  })
})
