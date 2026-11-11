import { useEffect, useRef, useState } from 'react'

import { useFriends } from '../../lib/useFriends'
import type { Friend } from '../../types/friend'
import { avatarTone, initials } from '../trips/tripUtils'
import { UserProfileModal } from './UserProfileModal'

const FOCUS_RING =
  'focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface focus-visible:outline-none'

function FriendsIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="h-4 w-4"
      aria-hidden="true"
    >
      <circle cx="9" cy="8" r="3.5" />
      <path d="M2.5 20a6.5 6.5 0 0 1 13 0" />
      <path d="M16 4.6a3.5 3.5 0 0 1 0 6.8M18.5 14.2A6.5 6.5 0 0 1 21.5 20" />
    </svg>
  )
}

/** Mounted only while the menu is open, so friends load on demand. */
function FriendsList({ onSelect }: { onSelect: (friend: Friend) => void }) {
  const { friends, isLoading } = useFriends()

  if (isLoading) return <p className="px-2 py-4 text-center text-sm text-faint">Loading friends…</p>
  if (friends.length === 0) {
    return (
      <p className="px-2 py-4 text-center text-sm text-faint">
        No friends yet. Click a member on a trip to add them.
      </p>
    )
  }

  return friends.map((friend) => (
    <button
      key={friend.userId}
      role="menuitem"
      type="button"
      onClick={() => onSelect(friend)}
      className={`flex w-full items-center gap-2 rounded-xl px-2 py-2 text-left transition-colors hover:bg-surface-subtle ${FOCUS_RING}`}
    >
      <span
        aria-hidden="true"
        className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-semibold text-foreground ${avatarTone(friend.userId)}`}
      >
        {initials(friend.displayName)}
      </span>
      <span className="truncate text-sm text-foreground">
        {friend.displayName ?? friend.email ?? 'Traveler'}
      </span>
    </button>
  ))
}

/** Navbar friends button; a friend opens their profile, where they can be removed (CGT-197). */
export function FriendsMenu() {
  const [isOpen, setIsOpen] = useState(false)
  const [selected, setSelected] = useState<Friend | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)

  // Close the dropdown on outside click or Escape.
  useEffect(() => {
    if (!isOpen) return
    function onMouseDown(event: MouseEvent) {
      if (!containerRef.current?.contains(event.target as Node)) setIsOpen(false)
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setIsOpen(false)
    }
    document.addEventListener('mousedown', onMouseDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onMouseDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [isOpen])

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setIsOpen((open) => !open)}
        aria-label="Friends"
        aria-haspopup="menu"
        aria-expanded={isOpen}
        title="Friends"
        className={`flex h-9 w-9 items-center justify-center rounded-full border border-border bg-surface-subtle text-muted transition-colors hover:border-border-strong hover:text-foreground ${FOCUS_RING}`}
      >
        <FriendsIcon />
      </button>

      {isOpen && (
        <div
          role="menu"
          aria-label="Friends"
          className="absolute right-0 z-50 mt-2 max-h-96 w-72 overflow-y-auto rounded-2xl border border-border bg-surface-raised p-2 shadow-2xl"
        >
          <p className="px-2 py-1.5 text-xs font-medium text-muted">Friends</p>
          <FriendsList
            onSelect={(friend) => {
              setSelected(friend)
              setIsOpen(false)
            }}
          />
        </div>
      )}

      {selected && (
        <UserProfileModal
          userId={selected.userId}
          displayName={selected.displayName}
          onClose={() => setSelected(null)}
        />
      )}
    </div>
  )
}
