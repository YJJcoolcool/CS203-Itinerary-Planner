/** Mirrors backend SuggestionStatus. */
export type SuggestionStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED'

/** The slice of a location shown alongside a suggestion. */
export interface SuggestionLocationRef {
  id: string
  name: string
  category: string | null
  latitude: number
  longitude: number
}

/** A rain-driven activity alternative proposed by the backend. */
export interface Suggestion {
  id: string
  tripId: string
  activityId: string
  activityName: string
  status: SuggestionStatus
  currentLocation: SuggestionLocationRef | null
  proposedLocation: SuggestionLocationRef
  reason: string
  explanation: string
  createdAt: string
  respondedAt: string | null
}
