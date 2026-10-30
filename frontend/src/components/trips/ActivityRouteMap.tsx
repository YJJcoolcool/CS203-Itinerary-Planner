import { useEffect } from 'react'
import {
  CircleMarker,
  MapContainer,
  Polyline,
  TileLayer,
  useMap,
} from 'react-leaflet'
import type { LatLngBoundsExpression } from 'leaflet'
import 'leaflet/dist/leaflet.css'

import type { RouteMapSegment } from '../../lib/routingUtils'
import type { RegionTiles } from '../../lib/api'

interface ActivityRouteMapProps {
  segments: RouteMapSegment[]
  tiles?: RegionTiles
}

const DEFAULT_TILE_URL = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
const DEFAULT_TILE_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'

const MODE_COLORS: Record<string, string> = {
  PUBLIC_TRANSPORT: '#2563eb',
  WALK: '#16a34a',
  SUBWAY: '#dc2626',
  BUS: '#87c700',
  RAIL: '#7c3aed',
  TRAM: '#d97706',
  FERRY: '#0891b2',
  DRIVING: '#2563eb',
  WALKING: '#16a34a',
  CYCLING: '#d97706',
}

function FitRouteBounds({
  segments,
}: ActivityRouteMapProps) {
  const map = useMap()

  useEffect(() => {
    const coordinates = segments.flatMap(
      (segment) => segment.coordinates,
    )

    if (coordinates.length > 0) {
      const bounds: LatLngBoundsExpression = coordinates
      map.fitBounds(bounds, { padding: [24, 24] })
    }
  }, [map, segments])

  return null
}

export default function ActivityRouteMap({
  segments,
  tiles,
}: ActivityRouteMapProps) {
  const coordinates = segments.flatMap(
    (segment) => segment.coordinates,
  )

  if (coordinates.length === 0) return null

  const start = coordinates[0]
  const end = coordinates[coordinates.length - 1]

  return (
    <div className="mt-3 h-72 w-full overflow-hidden rounded-xl z-0!">
        <style>
            {`
                .leaflet-container {
                    z-index: 0 !important;
                }
            `}
        </style>
        <MapContainer
            center={start}
            zoom={14}
            scrollWheelZoom={false}
            className="h-full w-full"
        >
            <TileLayer
            attribution={tiles?.attribution ?? DEFAULT_TILE_ATTRIBUTION}
            url={tiles?.url ?? DEFAULT_TILE_URL}
            />

            {segments.map((segment, index) => (
            <Polyline
                key={index}
                positions={segment.coordinates}
                pathOptions={{
                color: MODE_COLORS[segment.mode] ?? '#2563eb',
                weight: 5,
                opacity: 0.85,
                }}
            />
            ))}

            <CircleMarker
            center={start}
            radius={7}
            pathOptions={{
                color: '#ffffff',
                weight: 2,
                fillColor: '#16a34a',
                fillOpacity: 1,
            }}
            />

            <CircleMarker
            center={end}
            radius={7}
            pathOptions={{
                color: '#ffffff',
                weight: 2,
                fillColor: '#dc2626',
                fillOpacity: 1,
            }}
            />

            <FitRouteBounds segments={segments} />
        </MapContainer>
    </div>
  )
}