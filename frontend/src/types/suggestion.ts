/** Mirrors backend SuggestionStatus. UNDONE: applied, then reversed (CGT-169). */
export type SuggestionStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'UNDONE'

/** The slice of a location shown alongside a suggestion. */
export interface SuggestionLocationRef {
  id: string
  name: string
  category: string | null
  latitude: number
  longitude: number
}

/** One ranked alternative the user can choose for a rainy stop. */
export interface SuggestionOption {
  location: SuggestionLocationRef
  rank: number
  reason: string
  description: string
  aiPick: boolean
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
  /** The option actually applied; null until accepted. May be absent on older cached data. */
  acceptedLocation?: SuggestionLocationRef | null
  reason: string
  explanation: string
  /** Ordered best-first; 1–5 entries. May be absent on older cached data. */
  options: SuggestionOption[]
  createdAt: string
  respondedAt: string | null
}
