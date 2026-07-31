import { execFileSync } from 'node:child_process'
import type { Page } from '@playwright/test'

/**
 * Resolves the running backend container by Compose's `<project>-<service>-<index>`
 * naming convention, anchored on a "tawfir" project-name prefix — this dev
 * machine runs many unrelated projects side by side, some of which also have
 * a service literally named "backend" (e.g. "dars-ma-backend-1"), so an
 * unanchored substring match can silently read another project's logs.
 */
function backendContainerName(): string {
  const names = execFileSync('docker', ['ps', '--filter', 'name=^tawfir.*-backend-', '--format', '{{.Names}}'], {
    encoding: 'utf-8',
  })
    .split('\n')
    .map((n) => n.trim())
    .filter(Boolean)
  if (names.length === 0) {
    throw new Error('No running backend container found (name matching "^tawfir.*-backend-")')
  }
  return names[0]
}

/**
 * OTP delivery is mocked (MockOtpProvider) and only ever logs the code —
 * there is no test-only endpoint to fetch it, by design (security-tawfir.md
 * §6 flags any code-retrieval endpoint as a risk). Reading it back from the
 * backend container's own log output exercises the real send+verify flow
 * without adding new attack surface to the app itself.
 */
export function readLastMockOtpCode(phoneNumber: string): string {
  const last4 = phoneNumber.slice(-4)
  const logs = execFileSync('docker', ['logs', backendContainerName()], { encoding: 'utf-8' })
  const matches = [...logs.matchAll(new RegExp(`\\[MOCK OTP\\] phone=\\*\\*\\*${last4} code=(\\d+)`, 'g'))]
  const last = matches.at(-1)
  if (!last) {
    throw new Error(`No mock OTP code found in backend logs for phone ending ${last4}`)
  }
  return last[1]
}

export async function loginViaOtp(page: Page, phoneNumber: string): Promise<void> {
  await page.goto('/login')
  await page.getByLabel(/phone/i).fill(phoneNumber)
  await page.getByRole('button', { name: /send code/i }).click()
  // otp/request is async — wait for the step transition (code field render)
  // before reading it back from backend logs, otherwise we race the request.
  await page.getByLabel(/code/i).waitFor()
  const code = readLastMockOtpCode(phoneNumber)
  await page.getByLabel(/code/i).fill(code)
  await page.getByRole('button', { name: /verify/i }).click()
  await page.waitForURL('**/groups')
}
