import { apiFetch } from './apiClient'

export type DisputeStatus = 'OPEN' | 'ACCEPTED' | 'REJECTED'

export interface Dispute {
  id: string
  groupId: string
  ledgerEntryId: string
  raisedByUserId: string
  reason: string
  evidenceNote: string | null
  status: DisputeStatus
  resolvedByUserId: string | null
  resolutionReason: string | null
  createdAt: string
  resolvedAt: string | null
}

export interface OpenDisputeRequest {
  ledgerEntryId: string
  reason: string
  evidenceNote?: string
}

export interface ResolveDisputeRequest {
  resolution: 'ACCEPTED' | 'REJECTED'
  resolutionReason: string
}

export function listDisputes(groupId: string): Promise<Dispute[]> {
  return apiFetch<Dispute[]>(`/api/v1/groups/${groupId}/disputes`)
}

export function openDispute(groupId: string, request: OpenDisputeRequest): Promise<Dispute> {
  return apiFetch<Dispute>(`/api/v1/groups/${groupId}/disputes`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function resolveDispute(disputeId: string, request: ResolveDisputeRequest): Promise<Dispute> {
  return apiFetch<Dispute>(`/api/v1/disputes/${disputeId}/resolve`, {
    method: 'PATCH',
    body: JSON.stringify(request),
  })
}
