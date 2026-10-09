import { useId } from 'react'
import { useImportLocationFromOsm, useLocationSearch } from '../../lib/useTrips'
import type { GeoPlace, Location } from '../../types/trip'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const ADD_BUTTON = `inline-flex shrink-0 items-center justify-center rounded-lg border border-border bg-surface-subtle px-3 py-1.5 text-xs font-medium text-foreground transition-colors hover:border-accent hover:text-accent disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

const PILL =
  'inline-flex items-center rounded-full border border-border bg-surface-subtle px-2.5 py-0.5 font-mono text-[11px] text-muted'

/** Required OpenStreetMap licensing link shown wherever search results appear. */
const OSM_COPYRIGHT_URL = 'https://www.openstreetmap.org/copyright'
const OSM_ATTRIBUTION_FALLBACK = '© OpenStreetMap contributors'

/**
 * Debounced OpenStreetMap place search. Typing filters after a pause; choosing a
 * result imports it through `POST /api/locations/from-osm` and hands the stored
 * {@link Location} back to the parent via `onAdded`.
 */
export function LocationSearch({
  isAdded,
  onAdded,
}: {
  isAdded: (place: GeoPlace) => boolean
  onAdded: (location: Location) => void
}) {
  const { query, setQuery, results, attribution, isSearching, error, hasSearched } =
    useLocationSearch()
  const { importLocation, importingKey, error: importError } = useImportLocationFromOsm()

  const inputId = useId()
  const errorId = `${inputId}-error`
  const listLabelId = `${inputId}-results-label`
  const attributionId = `${inputId}-attribution`

  const trimmed = query.trim()
  const isIdle = trimmed.length === 0
  const showResults = !isIdle && !isSearching && !error && results.length > 0
  const showEmpty = !isIdle && !isSearching && !error && hasSearched && results.length === 0

  const status = isSearching
    ? 'Searching…'
    : showEmpty
      ? `No matches for ${trimmed}.`
      : showResults
        ? `${results.length} ${results.length === 1 ? 'result' : 'results'} for ${trimmed}.`
        : ''

  async function handleAdd(place: GeoPlace) {
    try {
      const location = await importLocation(place)
      onAdded(location)
    } catch {
      // The hook records the message in `importError`; nothing else to do here.
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={inputId} className="text-xs font-medium text-muted">
        Search for a place
      </label>
      <input
        id={inputId}
        type="search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        className="input-field"
        placeholder="e.g. Marina Bay Sands"
        autoComplete="off"
        spellCheck={false}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? errorId : attributionId}
      />

      {error && (
        <p id={errorId} role="alert" className="text-xs text-terracotta">
          {error}
        </p>
      )}

      <div aria-busy={isSearching} className="min-h-0">
        <p role="status" className="sr-only">
          {status}
        </p>

        {isSearching && (
          <ul aria-hidden="true" className="flex flex-col gap-2">
            {[0, 1, 2].map((row) => (
              <li
                key={row}
                className="h-12 animate-pulse rounded-xl border border-border bg-surface-subtle motion-reduce:animate-none"
              />
            ))}
          </ul>
        )}

        {showEmpty && <p className="text-xs text-faint">No matches for “{trimmed}”.</p>}

        {showResults && (
          <div>
            <p id={listLabelId} className="sr-only">
              Search results
            </p>
            <ul
              aria-labelledby={listLabelId}
              aria-describedby={attributionId}
              className="flex max-h-60 flex-col divide-y divide-border overflow-y-auto rounded-xl border border-border"
            >
              {results.map((place) => {
                const key = `${place.osmType}:${place.osmId}`
                const added = isAdded(place)
                const importing = importingKey === key
                const label = place.name ?? place.displayName ?? 'this place'

                return (
                  <li key={key} className="flex items-start justify-between gap-3 p-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm text-foreground">{label}</p>
                      {place.displayName && place.displayName !== label && (
                        <p className="truncate text-xs text-muted">{place.displayName}</p>
                      )}
                      {place.category && <span className={`${PILL} mt-1.5`}>{place.category}</span>}
                    </div>
                    <button
                      type="button"
                      onClick={() => void handleAdd(place)}
                      disabled={added || importing}
                      aria-label={added ? `${label} added` : `Add ${label}`}
                      className={ADD_BUTTON}
                    >
                      {added ? 'Added' : importing ? 'Adding…' : 'Add'}
                    </button>
                  </li>
                )
              })}
            </ul>
          </div>
        )}

        {importError && (
          <p role="alert" className="mt-2 text-xs text-terracotta">
            {importError}
          </p>
        )}
      </div>

      <p id={attributionId} className="text-[11px] text-faint">
        <a
          href={OSM_COPYRIGHT_URL}
          target="_blank"
          rel="noreferrer"
          className={`rounded underline underline-offset-2 transition-colors hover:text-foreground ${FOCUS_RING}`}
        >
          {attribution ?? OSM_ATTRIBUTION_FALLBACK}
        </a>
      </p>
    </div>
  )
}
