import { useState } from 'react'
import type { DragEvent } from 'react'

import type { Location } from '../../types/trip'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const ARROW_BUTTON = `flex h-7 w-7 items-center justify-center rounded-md text-muted transition-colors hover:bg-surface-subtle hover:text-foreground disabled:cursor-not-allowed disabled:opacity-30 ${FOCUS_RING}`

/**
 * The selected locations of an activity in visit order (CGT-152). Rows can be
 * dragged with a mouse, or moved with the up/down buttons (keyboard and touch).
 * The order shown is the order saved: the backend keeps it (CGT-151).
 */
export function LocationOrderList({
  locations,
  onMove,
  disabled = false,
}: {
  locations: Location[]
  /** Moves the location at index `from` to index `to`. */
  onMove: (from: number, to: number) => void
  disabled?: boolean
}) {
  // Index of the row being dragged; it follows the row as it moves.
  const [dragIndex, setDragIndex] = useState<number | null>(null)

  function handleDragStart(event: DragEvent<HTMLLIElement>, index: number) {
    setDragIndex(index)
    event.dataTransfer.effectAllowed = 'move'
    // Firefox only starts a drag when some data is set.
    event.dataTransfer.setData('text/plain', locations[index].id)
  }

  function handleDragOver(event: DragEvent<HTMLLIElement>, index: number) {
    if (dragIndex === null) return
    event.preventDefault() // allows dropping here
    if (dragIndex !== index) {
      onMove(dragIndex, index)
      setDragIndex(index)
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <p className="text-xs font-medium text-muted">
        Visit order <span className="font-normal text-faint">(drag, or use the arrows)</span>
      </p>

      <ol className="flex flex-col gap-1.5">
        {locations.map((location, index) => (
          <li
            key={location.id}
            draggable={!disabled}
            onDragStart={(event) => handleDragStart(event, index)}
            onDragOver={(event) => handleDragOver(event, index)}
            onDrop={(event) => event.preventDefault()}
            onDragEnd={() => setDragIndex(null)}
            className={`flex items-center gap-3 rounded-lg border bg-surface-raised px-2 py-1.5 ${
              dragIndex === index ? 'border-accent opacity-60' : 'border-border'
            } ${disabled ? '' : 'cursor-grab active:cursor-grabbing'}`}
          >
            <span aria-hidden="true" className="select-none text-faint">
              ⋮⋮
            </span>
            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-accent/15 font-mono text-xs text-accent">
              {index + 1}
            </span>
            <span className="min-w-0 flex-1 truncate text-sm text-foreground">{location.name}</span>

            <div className="flex shrink-0 items-center">
              <button
                type="button"
                onClick={() => onMove(index, index - 1)}
                disabled={disabled || index === 0}
                aria-label={`Move ${location.name} earlier`}
                className={ARROW_BUTTON}
              >
                ↑
              </button>
              <button
                type="button"
                onClick={() => onMove(index, index + 1)}
                disabled={disabled || index === locations.length - 1}
                aria-label={`Move ${location.name} later`}
                className={ARROW_BUTTON}
              >
                ↓
              </button>
            </div>
          </li>
        ))}
      </ol>
    </div>
  )
}
