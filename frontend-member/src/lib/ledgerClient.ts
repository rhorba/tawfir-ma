import { apiFetch } from './apiClient'

export type LedgerEntryType = 'CONTRIBUTION' | 'PAYOUT' | 'ADJUSTMENT'
export type LedgerSource = 'MEMBER_REPORTED' | 'ORGANIZER_CONFIRMED' | 'CMI_WEBHOOK' | 'ADMIN_ADJUSTMENT'

export interface LedgerEntry {
  id: string
  groupId: string
  entryType: LedgerEntryType
  contributionScheduleId: string | null
  payoutScheduleId: string | null
  actorUserId: string
  amount: number
  source: LedgerSource
  reversalOfEntryId: string | null
  createdAt: string
}

export function listLedger(groupId: string): Promise<LedgerEntry[]> {
  return apiFetch<LedgerEntry[]>(`/api/v1/groups/${groupId}/ledger`)
}
