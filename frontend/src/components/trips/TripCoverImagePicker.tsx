import { useState } from 'react'
import {
    TRIP_COVER_OPTIONS,
    TRIP_GALLERY_IMAGES,
    BROKEN_URL_IMAGE,
} from './tripCoverImages'
import { TripCoverGalleryModal } from './TripCoverGalleryModal'

const ALLOWED_IMAGE_EXTENSIONS = /\.(jpg|jpeg|png|webp)$/i
const MAX_URL_LENGTH = 2048

type CoverMode = 'default' | 'custom' | 'gallery'

interface TripCoverImagePickerProps {
    value: string | null
    onChange: (url: string | null) => void
    disabled?: boolean
}

function isValidImageUrl(value: string): boolean {
    if (value.length > MAX_URL_LENGTH) {
        return false
    }

    try {
        const url = new URL(value)

        return (
            url.protocol === 'https:' &&
            !url.username &&
            !url.password &&
            ALLOWED_IMAGE_EXTENSIONS.test(url.pathname)
        )
    } catch {
        return false
    }
}

function getInitialMode(value: string | null): CoverMode {
    if (!value) {
        return 'default'
    }

    if (TRIP_GALLERY_IMAGES.includes(value)) {
        return 'gallery'
    }

    return 'custom'
}

export function TripCoverImagePicker({
    value,
    onChange,
    disabled = false,
}: TripCoverImagePickerProps) {
    const [mode, setMode] = useState<CoverMode>(() => getInitialMode(value))
    const [customUrl, setCustomUrl] = useState(() =>
        value && !TRIP_GALLERY_IMAGES.includes(value) ? value : ''
    )
    const [galleryOpen, setGalleryOpen] = useState(false)

    function selectDefault() {
        setMode('default')
        onChange(null)
    }

    function selectCustom() {
      setMode('custom')

      const trimmedUrl = customUrl.trim()

      onChange(
          trimmedUrl && isValidImageUrl(trimmedUrl)
              ? trimmedUrl
              : null
      )
    }

    function openGallery() {
        setGalleryOpen(true)
    }

    function confirmGallerySelection(imageUrl: string) {
        setMode('gallery')
        setCustomUrl('')
        setGalleryOpen(false)
        onChange(imageUrl)
    }

    function handleCustomUrlChange(nextUrl: string) {
        setCustomUrl(nextUrl)

        const trimmedUrl = nextUrl.trim()

        if (trimmedUrl && isValidImageUrl(trimmedUrl)) {
            onChange(trimmedUrl)
        } else {
            onChange(null)
        }
    }

    const cards = TRIP_COVER_OPTIONS.map((option) => ({
        mode: option.id as CoverMode,
        title: option.label,
        image:
            option.id === 'custom' && isValidImageUrl(customUrl.trim())
                ? customUrl.trim()
                : option.id === 'gallery' &&
                    value &&
                    TRIP_GALLERY_IMAGES.includes(value)
                    ? value
                    : option.url,
        onClick:
            option.id === 'default'
                ? selectDefault
                : option.id === 'custom'
                    ? selectCustom
                    : openGallery,
    }))

    return (
        <div className="flex flex-col gap-4">
            <div>
                <h3 className="text-sm font-semibold">
                    Trip cover
                </h3>
                <p className="mt-1 text-sm text-gray-500">
                    Choose how your trip cover image should be displayed.
                </p>
            </div>

            {/* Cover options */}
            <div className="grid grid-cols-3 gap-3">
                {cards.map((card) => {
                    const isSelected = mode === card.mode

                    return (
                        <button
                            key={card.mode}
                            type="button"
                            disabled={disabled}
                            onClick={card.onClick}
                            aria-pressed={isSelected}
                            className={`overflow-hidden rounded-xl border-2 text-left transition ${
                                isSelected
                                    ? 'border-blue-500 ring-2 ring-blue-500/20'
                                    : 'border-gray-200 hover:border-gray-400 dark:border-slate-700 dark:hover:border-slate-500'
                            } disabled:cursor-not-allowed disabled:opacity-50`}
                        >
                            <img
                                src={card.image}
                                alt=""
                                onError={(event) => {
                                    event.currentTarget.onerror = null

                                    if (card.mode === 'custom') {
                                        event.currentTarget.src = BROKEN_URL_IMAGE
                                    } else if (card.mode === 'gallery') {
                                        event.currentTarget.src = BROKEN_URL_IMAGE
                                    }
                                }}
                                className="aspect-4/3 w-full object-cover"
                            />

                            <div className="flex min-h-12 items-center justify-center p-2 text-center">
                                <span className="text-xs font-medium sm:text-sm">
                                    {card.title}
                                </span>
                            </div>
                        </button>
                    )
                })}
            </div>

            {/* Custom URL input */}
            {mode === 'custom' && (
                <div className="flex flex-col gap-2">
                    <label
                        htmlFor="trip-cover-custom-url"
                        className="text-sm font-medium"
                    >
                        Image URL
                    </label>

                    <input
                        id="trip-cover-custom-url"
                        type="url"
                        value={customUrl}
                        onChange={(event) =>
                            handleCustomUrlChange(event.target.value)
                        }
                        disabled={disabled}
                        maxLength={MAX_URL_LENGTH}
                        placeholder="https://example.com/image.jpg"
                        className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-600 dark:bg-slate-800"
                    />

                    {customUrl.trim() &&
                        !isValidImageUrl(customUrl.trim()) && (
                            <p className="text-xs text-red-500">
                                Enter a valid HTTPS image URL ending in
                                .jpg, .jpeg, .png, or .webp.
                            </p>
                        )}
                </div>
            )}

            {/* Gallery modal */}
            {galleryOpen && (
                <TripCoverGalleryModal
                    initialSelection={
                        value && TRIP_GALLERY_IMAGES.includes(value)
                            ? value
                            : null
                    }
                    onConfirm={confirmGallerySelection}
                    onCancel={() => setGalleryOpen(false)}
                />
            )}
        </div>
    )
}