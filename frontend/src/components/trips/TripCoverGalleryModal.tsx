import { useState } from 'react'

import { useBodyScrollLock } from '../../lib/useBodyScrollLock'
import { TRIP_GALLERY_IMAGES } from './tripCoverImages'

interface TripCoverGalleryModalProps {
    initialSelection: string | null
    onConfirm: (imageUrl: string) => void
    onCancel: () => void
}

export function TripCoverGalleryModal({
    initialSelection,
    onConfirm,
    onCancel,
}: TripCoverGalleryModalProps) {
    const [selectedImage, setSelectedImage] = useState<string | null>(
        initialSelection
    )

    // Prevent background scrolling
    useBodyScrollLock();

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
            onClick={onCancel}
        >
            <div
                role="dialog"
                aria-modal="true"
                aria-labelledby="trip-cover-gallery-title"
                className="flex max-h-[85vh] w-full max-w-2xl flex-col rounded-xl bg-white p-5 shadow-xl dark:bg-slate-900"
                onClick={(event) => event.stopPropagation()}
            >
                {/* Header */}
                <div className="mb-4 flex items-center justify-between">
                    <h2
                        id="trip-cover-gallery-title"
                        className="text-lg font-semibold"
                    >
                        Choose a cover image
                    </h2>

                    <button
                        type="button"
                        onClick={onCancel}
                        className="rounded-lg p-2 text-gray-500 hover:bg-gray-100 dark:hover:bg-slate-800"
                        aria-label="Close gallery"
                    >
                        ✕
                    </button>
                </div>

                {/* Scrollable gallery */}
                <div className="min-h-0 flex-1 overflow-y-auto">
                    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
                        {TRIP_GALLERY_IMAGES.map((imageUrl) => {
                            const isSelected = selectedImage === imageUrl

                            return (
                                <button
                                    key={imageUrl}
                                    type="button"
                                    onClick={() => setSelectedImage(imageUrl)}
                                    aria-label={`Select ${imageUrl.split('/').pop()}`}
                                    aria-pressed={isSelected}
                                    className={`relative aspect-4/3 overflow-hidden rounded-lg border-2 transition ${
                                        isSelected
                                            ? 'border-blue-500 ring-2 ring-blue-500/30'
                                            : 'border-transparent hover:border-gray-300 dark:hover:border-slate-600'
                                    }`}
                                >
                                    <img
                                        src={imageUrl}
                                        alt=""
                                        className="h-full w-full object-cover"
                                    />

                                    {isSelected && (
                                        <span className="absolute right-2 top-2 flex h-6 w-6 items-center justify-center rounded-full bg-blue-500 text-sm text-white">
                                            ✓
                                        </span>
                                    )}
                                </button>
                            )
                        })}
                    </div>
                </div>

                {/* Footer */}
                <div className="mt-5 flex justify-end gap-3 border-t border-gray-200 pt-4 dark:border-slate-700">
                    <button
                        type="button"
                        onClick={onCancel}
                        className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium hover:bg-gray-100 dark:border-slate-600 dark:hover:bg-slate-800"
                    >
                        Cancel
                    </button>

                    <button
                        type="button"
                        disabled={!selectedImage}
                        onClick={() => {
                            if (selectedImage) {
                                onConfirm(selectedImage)
                            }
                        }}
                        className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                        OK
                    </button>
                </div>
            </div>
        </div>
    )
}