import { apiFetch } from './apiClient'

export type ContributionStatus = 'PENDING' | 'MARKED_PAID' | 'CONFIRMED' | 'LATE' | 'DISPUTED'

export interface Contribution {
  id: string
  groupId: string
  cycleNumber: number
  userId: string
  dueDate: string
  status: ContributionStatus
}

export function listContributions(groupId: string): Promise<Contribution[]> {
  return apiFetch<Contribution[]>(`/api/v1/groups/${groupId}/contributions`)
}

export function markPaid(groupId: string, scheduleId: string): Promise<Contribution> {
  return apiFetch<Contribution>(`/api/v1/groups/${groupId}/contributions/${scheduleId}/mark-paid`, {
    method: 'POST',
  })
}

export function confirmContribution(groupId: string, scheduleId: string): Promise<Contribution> {
  return apiFetch<Contribution>(`/api/v1/groups/${groupId}/contributions/${scheduleId}/confirm`, {
    method: 'POST',
  })
}
