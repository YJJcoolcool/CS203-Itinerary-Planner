
import { useState } from 'react'
import { createPortal } from 'react-dom'
import {
  MapContainer,
  Marker,
  TileLayer,
  useMapEvents,
} from 'react-leaflet'
import { divIcon, type LatLng } from 'leaflet'
import 'leaflet/dist/leaflet.css'

import type { Location } from '../../types/trip'
import type { ManualIndoorClass, RegionTiles } from '../../lib/api'
import { useCreateLocation } from '../../lib/useTrips'

/** The three choices for CGT-141; "Not sure" sends null so rain checks skip the place. */
const INDOOR_OPTIONS: { value: ManualIndoorClass; label: string }[] = [
  { value: 'OUTDOOR', label: 'Outdoor' },
  { value: 'INDOOR', label: 'Indoor' },
  { value: null, label: 'Not sure' },
]

interface ManualLocationModalProps {
  /** The trip the new place belongs to; only its members can see it (CGT-142). */
  tripId: string
  onClose: () => void
  onCreated: (location: Location) => void
  tiles?: RegionTiles
  initialCenter?: Coordinates
}

interface Coordinates {
  latitude: number
  longitude: number
}

const INITIAL_COORDINATES: Coordinates = {
  latitude: 1.3521,
  longitude: 103.8198,
}

const DEFAULT_TILE_URL = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
const DEFAULT_TILE_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'

const markerIcon = divIcon({
  className: '',
  html: `
    <div style="
      width: 28px;
      height: 28px;
      background: #2563eb;
      border: 3px solid white;
      border-radius: 50% 50% 50% 0;
      transform: rotate(-45deg);
      box-shadow: 0 2px 6px rgba(0,0,0,0.35);
    "></div>
  `,
  iconSize: [28, 28],
  iconAnchor: [14, 28],
})

function MapClickHandler({
  onPositionChange,
}: {
  onPositionChange: (coordinates: Coordinates) => void
}) {
  useMapEvents({
    click(event: { latlng: LatLng }) {
      onPositionChange({
        latitude: event.latlng.lat,
        longitude: event.latlng.lng,
      })
    },
  })

  return null
}

export default function ManualLocationModal({
  tripId,
  onClose,
  onCreated,
  tiles,
  initialCenter,
}: ManualLocationModalProps) {
  const startingCoordinates = initialCenter ?? INITIAL_COORDINATES
  const [name, setName] = useState('')
  const [coordinates, setCoordinates] =
    useState<Coordinates>(startingCoordinates)
  const [latitudeInput, setLatitudeInput] = useState(
    String(startingCoordinates.latitude),
  )
  const [longitudeInput, setLongitudeInput] = useState(
    String(startingCoordinates.longitude),
  )
  const [validationError, setValidationError] = useState<string | null>(
    null,
  )
  const [indoorClass, setIndoorClass] = useState<ManualIndoorClass>(null)

  const { createLocation, isCreating, error } = useCreateLocation(tripId)

  function updateCoordinates(next: Coordinates) {
    setCoordinates(next)
    setLatitudeInput(String(next.latitude))
    setLongitudeInput(String(next.longitude))
    setValidationError(null)
  }

  function handleLatitudeChange(value: string) {
    setLatitudeInput(value)
    setValidationError(null)

    const latitude = Number(value)

    if (
      value.trim() !== '' &&
      Number.isFinite(latitude) &&
      latitude >= -90 &&
      latitude <= 90
    ) {
      setCoordinates((current) => ({
        ...current,
        latitude,
      }))
    }
  }

  function handleLongitudeChange(value: string) {
    setLongitudeInput(value)
    setValidationError(null)

    const longitude = Number(value)

    if (
      value.trim() !== '' &&
      Number.isFinite(longitude) &&
      longitude >= -180 &&
      longitude <= 180
    ) {
      setCoordinates((current) => ({
        ...current,
        longitude,
      }))
    }
  }

  function validate(): string | null {
    if (!name.trim()) {
      return 'Please enter a location name.'
    }

    if (latitudeInput.trim() === '') {
      return 'Please enter a latitude.'
    }

    if (longitudeInput.trim() === '') {
      return 'Please enter a longitude.'
    }

    const latitude = Number(latitudeInput)
    const longitude = Number(longitudeInput)

    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
      return 'Latitude must be between -90 and 90.'
    }

    if (
      !Number.isFinite(longitude) ||
      longitude < -180 ||
      longitude > 180
    ) {
      return 'Longitude must be between -180 and 180.'
    }

    return null
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setValidationError(null)

    const problem = validate()

    if (problem) {
      setValidationError(problem)
      return
    }

    try {
      const location = await createLocation({
        name: name.trim(),
        latitude: Number(latitudeInput),
        longitude: Number(longitudeInput),
        indoorClass,
      })

      onCreated(location)
      onClose()
    } catch {
      // The useCreateLocation hook exposes the API error.
      // Keep the modal open so the user can retry.
    }
  }

  function handleKeyDown(event: React.KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape' && !isCreating) {
      onClose()
    }
  }

  return createPortal(
    <div
      className="fixed inset-0 z-[70] flex items-center justify-center bg-black/50 p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !isCreating) {
          onClose()
        }
      }}
      onKeyDown={handleKeyDown}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="manual-location-title"
        className="w-full max-w-xl overflow-hidden rounded-xl border border-border bg-surface-raised text-foreground shadow-xl"
      >
        <form onSubmit={handleSubmit}>
          <header className="flex items-center justify-between border-b border-border px-5 py-4">
            <div>
              <h2
                id="manual-location-title"
                className="text-lg font-semibold text-foreground"
              >
                Add location manually
              </h2>
              <p className="mt-1 text-sm text-muted">
                Enter a name and coordinates, or choose a point on the map.
              </p>
            </div>

            <button
              type="button"
              onClick={onClose}
              disabled={isCreating}
              aria-label="Close"
              className="rounded-md p-2 text-muted transition-colors hover:bg-surface-subtle hover:text-foreground disabled:opacity-50"
            >
              ✕
            </button>
          </header>

          <div className="max-h-[75vh] space-y-4 overflow-y-auto p-5">
            <div className="space-y-1.5">
              <label
                htmlFor="manual-location-name"
                className="block text-sm font-medium text-foreground"
              >
                Name
              </label>
              <input
                id="manual-location-name"
                type="text"
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="e.g. My favourite viewpoint"
                maxLength={255}
                required
                autoFocus
                className="w-full rounded-md border border-border bg-surface-subtle px-3 py-2 text-sm text-foreground outline-none placeholder:text-faint focus:border-accent focus:ring-2 focus:ring-accent/20"
              />
            </div>

            <fieldset className="space-y-1.5">
              <legend className="block text-sm font-medium text-foreground">
                Indoor or outdoor?
              </legend>
              <div
                role="radiogroup"
                aria-label="Indoor or outdoor"
                className="inline-flex rounded-md border border-border bg-surface-subtle p-1"
              >
                {INDOOR_OPTIONS.map((option) => {
                  const isSelected = indoorClass === option.value
                  return (
                    <button
                      key={option.label}
                      type="button"
                      role="radio"
                      aria-checked={isSelected}
                      onClick={() => setIndoorClass(option.value)}
                      disabled={isCreating}
                      className={`rounded px-3 py-1.5 text-sm transition-colors disabled:opacity-50 ${
                        isSelected
                          ? 'bg-accent text-accent-contrast'
                          : 'text-muted hover:text-foreground'
                      }`}
                    >
                      {option.label}
                    </button>
                  )
                })}
              </div>
              <p className="text-xs text-muted">
                Outdoor places get a rain warning and an indoor alternative when bad weather is forecast.
              </p>
            </fieldset>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <label
                  htmlFor="manual-location-latitude"
                  className="block text-sm font-medium text-foreground"
                >
                  Latitude
                </label>
                <input
                  id="manual-location-latitude"
                  type="number"
                  step="any"
                  min="-90"
                  max="90"
                  value={latitudeInput}
                  onChange={(event) =>
                    handleLatitudeChange(event.target.value)
                  }
                  placeholder="1.3521"
                  required
                  className="w-full rounded-md border border-border bg-surface-subtle px-3 py-2 text-sm text-foreground outline-none placeholder:text-faint focus:border-accent focus:ring-2 focus:ring-accent/20"
                />
                <p className="text-xs text-muted">
                  From -90 to 90
                </p>
              </div>

              <div className="space-y-1.5">
                <label
                  htmlFor="manual-location-longitude"
                  className="block text-sm font-medium text-foreground"
                >
                  Longitude
                </label>
                <input
                  id="manual-location-longitude"
                  type="number"
                  step="any"
                  min="-180"
                  max="180"
                  value={longitudeInput}
                  onChange={(event) =>
                    handleLongitudeChange(event.target.value)
                  }
                  placeholder="103.8198"
                  required
                  className="w-full rounded-md border border-border bg-surface-subtle px-3 py-2 text-sm text-foreground outline-none placeholder:text-faint focus:border-accent focus:ring-2 focus:ring-accent/20"
                />
                <p className="text-xs text-muted">
                  From -180 to 180
                </p>
              </div>
            </div>

            <div className="space-y-2">
              <div>
                <h3 className="text-sm font-medium text-foreground">
                  Choose on map
                </h3>
                <p className="text-xs text-muted">
                  Click anywhere on the map or drag the marker to set the
                  coordinates.
                </p>
              </div>

              <div className="h-[300px] overflow-hidden rounded-lg border border-border">
                <MapContainer
                  center={[
                    coordinates.latitude,
                    coordinates.longitude,
                  ]}
                  zoom={13}
                  scrollWheelZoom
                  className="h-full w-full"
                >
                  <TileLayer
                    attribution={tiles?.attribution ?? DEFAULT_TILE_ATTRIBUTION}
                    url={tiles?.url ?? DEFAULT_TILE_URL}
                  />

                  <MapClickHandler
                    onPositionChange={updateCoordinates}
                  />

                  <Marker
                    position={[
                      coordinates.latitude,
                      coordinates.longitude,
                    ]}
                    icon={markerIcon}
                    draggable
                    eventHandlers={{
                      dragend(event) {
                        const position = event.target.getLatLng()
                        updateCoordinates({
                          latitude: position.lat,
                          longitude: position.lng,
                        })
                      },
                    }}
                  />
                </MapContainer>
              </div>

              <p className="text-xs text-muted">
                Selected: {coordinates.latitude.toFixed(6)},{' '}
                {coordinates.longitude.toFixed(6)}
              </p>
            </div>

            {(validationError || error) && (
              <div
                role="alert"
                className="rounded-md border border-terracotta/40 bg-terracotta/10 px-3 py-2 text-sm text-terracotta"
              >
                {validationError || error}
              </div>
            )}
          </div>

          <footer className="flex justify-end gap-2 border-t border-border px-5 py-4">
            <button
              type="button"
              onClick={onClose}
              disabled={isCreating}
              className="rounded-md border border-border px-4 py-2 text-sm font-medium text-foreground transition-colors hover:border-border-strong hover:bg-surface-subtle disabled:cursor-not-allowed disabled:opacity-50"
            >
              Cancel
            </button>

            <button
              type="submit"
              disabled={isCreating}
              className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-accent-contrast transition-colors hover:bg-accent-strong disabled:cursor-not-allowed disabled:opacity-50"
            >
              {isCreating ? 'Saving...' : 'Save location'}
            </button>
          </footer>
        </form>
      </section>
    </div>,
    document.body,
  )
}
