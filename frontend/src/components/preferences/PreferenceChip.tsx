const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

/**
 * One selectable preference chip. Renders a toggle button when `onToggle` is
 * supplied and a static chip otherwise, so read-only views keep the same look.
 */
export function PreferenceChip({
  label,
  selected,
  onToggle,
  disabled = false,
}: {
  label: string
  selected: boolean
  /** Omit for a read-only chip. */
  onToggle?: () => void
  disabled?: boolean
}) {
  const tone = selected
    ? 'border-accent/50 bg-accent-soft text-accent hover:border-accent'
    : 'border-border bg-surface-subtle text-muted hover:border-border-strong hover:text-foreground'

  if (!onToggle) {
    return (
      <span className={`inline-flex items-center rounded-full border px-3 py-1.5 text-sm ${tone}`}>
        {label}
      </span>
    )
  }

  return (
    <button
      type="button"
      aria-pressed={selected}
      disabled={disabled}
      onClick={onToggle}
      className={`inline-flex items-center rounded-full border px-3 py-1.5 text-sm transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${tone} ${FOCUS_RING}`}
    >
      {label}
    </button>
  )
}
