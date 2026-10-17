/** Mirrors backend InvitationStatus. */
export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED'

/** Access level granted by an invitation. */
export type TripRole = 'EDITOR' | 'VIEWER'

/** Display label for a trip role. */
export function roleLabel(role: TripRole): 'Editor' | 'Viewer' {
  return role === 'EDITOR' ? 'Editor' : 'Viewer'
}

/** Mirrors TripInvitationResponse (flat DTO; no emails or full user/trip objects). */
export interface TripInvitation {
  /** 8-char alphanumeric id. */
  id: string
  tripId: string
  tripName: string
  /** ISO date string, e.g. `2026-11-02`. */
  tripStartDate: string
  tripEndDate: string
  senderDisplayName: string | null
  receiverDisplayName: string | null
  /** Access level the invitation grants. */
  role: TripRole
  status: InvitationStatus
  /** ISO-8601 instant, e.g. `2026-10-08T06:12:45Z`. */
  createdAt: string
}
