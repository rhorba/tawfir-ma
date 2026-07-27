import { apiFetch } from './apiClient'

export type Frequency = 'WEEKLY' | 'MONTHLY'
export type PayoutOrderMode = 'MANUAL' | 'RANDOMIZED'
export type GroupStatus = 'DRAFT' | 'FINALIZED' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED'
export type MembershipRole = 'MEMBER' | 'ORGANIZER'

export interface CreateGroupRequest {
  name: string
  contributionAmount: number
  frequency: Frequency
  totalCycles: number
  payoutOrderMode: PayoutOrderMode
  members: string[]
}

export interface Member {
  userId: string
  phoneNumber: string
  roleInGroup: MembershipRole
  payoutPosition: number | null
}

export interface GroupSummary {
  id: string
  name: string
  contributionAmount: number
  currency: string
  frequency: Frequency
  totalCycles: number
  status: GroupStatus
  memberCount: number
}

export interface GroupDetail {
  id: string
  name: string
  organizerId: string
  contributionAmount: number
  currency: string
  frequency: Frequency
  totalCycles: number
  payoutOrderMode: PayoutOrderMode
  status: GroupStatus
  members: Member[]
}

export function createGroup(request: CreateGroupRequest): Promise<GroupDetail> {
  return apiFetch<GroupDetail>('/api/v1/groups', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function finalizeGroup(groupId: string): Promise<GroupDetail> {
  return apiFetch<GroupDetail>(`/api/v1/groups/${groupId}/finalize`, {
    method: 'POST',
  })
}

export function listGroups(): Promise<GroupSummary[]> {
  return apiFetch<GroupSummary[]>('/api/v1/groups')
}

export function getGroup(groupId: string): Promise<GroupDetail> {
  return apiFetch<GroupDetail>(`/api/v1/groups/${groupId}`)
}
