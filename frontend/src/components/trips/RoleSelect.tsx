import { useEffect, useId, useRef, useState } from 'react'

import { roleLabel } from '../../types/invitation'
import type { TripRole } from '../../types/invitation'

const ROLES: TripRole[] = ['VIEWER', 'EDITOR']

/** Viewer/Editor picker styled like the project's other popovers (CountrySelect, DateTimePicker). */
export function RoleSelect({
  value,
  onChange,
  disabled = false,
  id,
  ariaLabel,
  className = '',
}: {
  value: TripRole
  onChange: (role: TripRole) => void
  disabled?: boolean
  id?: string
  ariaLabel: string
  /** Width classes for the trigger, e.g. `w-28`. */
  className?: string
}) {
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const listId = useId()

  useEffect(() => {
    if (!isOpen) return

    function onPointerDown(event: PointerEvent) {
      if (!containerRef.current?.contains(event.target as Node)) setIsOpen(false)
    }
    // Captured so Escape closes only this list, not the settings panel around it.
    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Escape') return
      event.stopPropagation()
      setIsOpen(false)
      triggerRef.current?.focus()
    }

    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown, true)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown, true)
    }
  }, [isOpen])

  function select(role: TripRole) {
    setIsOpen(false)
    triggerRef.current?.focus()
    if (role !== value) onChange(role)
  }

  return (
    <div ref={containerRef} className={`relative ${className}`}>
      <button
        ref={triggerRef}
        id={id}
        type="button"
        disabled={disabled}
        onClick={() => setIsOpen((open) => !open)}
        aria-label={`${ariaLabel}: ${roleLabel(value)}`}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        aria-controls={isOpen ? listId : undefined}
        className={`input-field flex w-full items-center justify-between gap-2 text-left transition-colors hover:border-border-strong disabled:cursor-not-allowed disabled:opacity-50 ${isOpen ? 'border-accent' : ''}`}
      >
        <span>{roleLabel(value)}</span>
        <svg
          aria-hidden="true"
          viewBox="0 0 20 20"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
          className={`h-4 w-4 shrink-0 text-muted transition-transform ${isOpen ? 'rotate-180' : ''}`}
        >
          <path d="m5 7.5 5 5 5-5" />
        </svg>
      </button>

      {isOpen && (
        <ul
          id={listId}
          role="listbox"
          aria-label={ariaLabel}
          className="absolute right-0 z-30 mt-1 w-full min-w-28 rounded-xl border border-border bg-surface-raised p-1 shadow-xl"
        >
          {ROLES.map((role) => {
            const isSelected = role === value
            return (
              <li key={role}>
                <button
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  autoFocus={isSelected}
                  onClick={() => select(role)}
                  className={`flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-sm transition-colors hover:bg-surface-subtle focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent ${
                    isSelected ? 'font-medium text-accent' : 'text-foreground'
                  }`}
                >
                  {roleLabel(role)}
                  {isSelected && (
                    <svg
                      aria-hidden="true"
                      viewBox="0 0 20 20"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      className="h-4 w-4"
                    >
                      <path d="m5 10.5 3.2 3L15 6.5" />
                    </svg>
                  )}
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
