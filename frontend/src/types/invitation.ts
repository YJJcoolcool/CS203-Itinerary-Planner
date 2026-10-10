/** Mirrors backend InvitationStatus. */
export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED'

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
  status: InvitationStatus
  /** ISO-8601 instant, e.g. `2026-10-08T06:12:45Z`. */
  createdAt: string
}
