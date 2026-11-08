/** Mirrors FriendService.Relation: how the signed-in user relates to someone else. */
export type FriendRelation = 'NONE' | 'OUTGOING' | 'INCOMING' | 'FRIENDS'

/** Mirrors FriendService.RelationResponse. `requestId` is set while a request is pending. */
export interface Friendship {
  relation: FriendRelation
  requestId: string | null
}

/** An accepted friend. Email is shared between friends to prefill trip invites. */
export interface Friend {
  userId: string
  displayName: string | null
  email: string | null
}

/** A pending request addressed to the signed-in user. */
export interface FriendRequest {
  id: string
  /** The requester. */
  userId: string
  displayName: string | null
  /** ISO-8601 instant. */
  createdAt: string
}
