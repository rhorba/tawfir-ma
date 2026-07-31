import { test, expect } from '@playwright/test'
import { mkdirSync, copyFileSync } from 'node:fs'
import path from 'node:path'
import { loginViaOtp } from './helpers'

/**
 * `use.video: 'on'` in playwright.config.ts only auto-attaches to the
 * built-in `context`/`page` fixtures — this test drives two independent
 * logged-in users via manual `browser.newContext()` calls, which bypasses
 * that wiring entirely, so recordVideo has to be passed explicitly per
 * context here for CLAUDE.md rule 9's recording to actually be produced.
 */
const RECORDINGS_DIR = path.resolve(process.cwd(), '..', '.recordings')

/**
 * End-to-end coverage of the vertical slice shipped through Sprint 3:
 * auth -> group creation/finalize -> contribution mark-paid/confirm ->
 * dispute open/resolve. Runs against the docker-compose stack (real
 * Postgres, real backend, real frontend-member build) per CLAUDE.md rule 9.
 */
test('organizer and member walk the full group lifecycle including a dispute', async ({ browser }) => {
  const organizerPhone = '+212711000001'
  const memberPhone = '+212711000002'

  const organizerContext = await browser.newContext({ recordVideo: { dir: 'test-results/videos' } })
  const organizerPage = await organizerContext.newPage()
  await loginViaOtp(organizerPage, organizerPhone)

  await organizerPage.getByRole('link', { name: /create group/i }).click()
  await organizerPage.getByLabel(/group name/i).fill('E2E Daret')
  await organizerPage.getByLabel(/contribution amount/i).fill('200')
  await organizerPage.getByLabel(/number of cycles/i).fill('2')
  await organizerPage.getByLabel(/payout order/i).selectOption('MANUAL')
  await organizerPage.getByLabel('Member 1 phone number').fill(organizerPhone)
  await organizerPage.getByRole('button', { name: /add member/i }).click()
  await organizerPage.getByLabel('Member 2 phone number').fill(memberPhone)
  await organizerPage.getByRole('button', { name: /continue/i }).click()

  await organizerPage.waitForURL(/\/groups\/[\w-]+$/)
  await expect(organizerPage.getByText('E2E Daret')).toBeVisible()

  await organizerPage.getByRole('button', { name: /finalize group/i }).click()
  await expect(organizerPage.getByText(/ACTIVE/)).toBeVisible()
  const groupUrl = organizerPage.url()

  const memberContext = await browser.newContext({ recordVideo: { dir: 'test-results/videos' } })
  const memberPage = await memberContext.newPage()
  await loginViaOtp(memberPage, memberPhone)
  await memberPage.goto(groupUrl)
  await expect(memberPage.getByText('E2E Daret')).toBeVisible()

  await memberPage.getByRole('button', { name: /mark paid/i }).first().click()
  await expect(memberPage.getByText(/MARKED_PAID/)).toBeVisible()

  await organizerPage.reload()
  await organizerPage.getByRole('button', { name: /confirm/i }).click()
  await expect(organizerPage.getByText(/CONFIRMED/)).toBeVisible()

  await memberPage.reload()
  await memberPage.getByRole('button', { name: 'Dispute', exact: true }).click()
  await memberPage.getByLabel(/dispute reason/i).fill('Amount looks wrong to me')
  await memberPage.getByRole('button', { name: /submit/i }).click()
  await expect(memberPage.getByText('DISPUTED')).toBeVisible()

  await organizerPage.reload()
  await organizerPage.getByRole('button', { name: /^disputes$/i }).click()
  await expect(organizerPage.getByText('Amount looks wrong to me')).toBeVisible()
  await organizerPage.getByLabel(/resolution reason/i).fill('Verified the amount was correct after all')
  await organizerPage.getByRole('button', { name: /^reject$/i }).click()
  await expect(organizerPage.getByText('REJECTED')).toBeVisible()

  const organizerVideo = organizerPage.video()
  const memberVideo = memberPage.video()
  await organizerContext.close()
  await memberContext.close()

  mkdirSync(RECORDINGS_DIR, { recursive: true })
  const dateStamp = new Date().toISOString().slice(0, 10)
  if (organizerVideo) {
    copyFileSync(await organizerVideo.path(), path.join(RECORDINGS_DIR, `v3-${dateStamp}-organizer.webm`))
  }
  if (memberVideo) {
    copyFileSync(await memberVideo.path(), path.join(RECORDINGS_DIR, `v3-${dateStamp}-member.webm`))
  }
})
