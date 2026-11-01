export const DEFAULT_COVER_IMAGE = '/images/trip_covers/default-cover.png'
export const CUSTOM_URL_CARD_IMAGE = '/images/trip_covers/custom-url.png'
export const GALLERY_CARD_IMAGE = '/images/trip_covers/gallery.png'
export const BROKEN_URL_IMAGE = '/images/trip_covers/broken-url.png'

export interface TripCoverOption {
  id: string
  label: string
  url: string
}

/**
 * The three cover selection cards displayed in Trip Settings.
 */
export const TRIP_COVER_OPTIONS: TripCoverOption[] = [
  {
    id: 'default',
    label: 'Default',
    url: DEFAULT_COVER_IMAGE,
  },
  {
    id: 'custom',
    label: 'Custom Image URL',
    url: CUSTOM_URL_CARD_IMAGE,
  },
  {
    id: 'gallery',
    label: 'Choose from Gallery',
    url: GALLERY_CARD_IMAGE,
  },
]

/**
 * All available trip cover images.
 *
 * Add new gallery images here when adding files.
 * Supported formats: jpg, jpeg, png, webp.
 */
const ALL_TRIP_COVER_IMAGES = [
  '/images/trip_covers/pexels-julia-khalimova-826746-4140892.jpg',
  '/images/trip_covers/pexels-realcereal-15480502.jpg',
  '/images/trip_covers/pexels-ruiz-15275312.jpg',
  '/images/trip_covers/pexels-pawan-yadav-1321878-5870526.jpg',
  '/images/trip_covers/1280px-Lake_Wakatipu_With_Seagull_and_the_TSS_Earnslaw_–_December_2025.jpg',
  '/images/trip_covers/pexels-silverkblack-36767162.jpg',
  '/images/trip_covers/pexels-jimmy-liao-3615017-11720365.jpg',
  '/images/trip_covers/pexels-suju-16725673.jpg',
  '/images/trip_covers/pexels-sudeep-kumar-173738022-23885155.jpg',
  '/images/trip_covers/pexels-chaitaastic-8821401.jpg',
]

/**
 * Images reserved for the selection cards.
 * These should not appear in the gallery.
 */
const EXCLUDED_GALLERY_IMAGES = [
  DEFAULT_COVER_IMAGE,
  CUSTOM_URL_CARD_IMAGE,
  GALLERY_CARD_IMAGE,
]

/**
 * Gallery images, excluding the three special card images.
 */
export const TRIP_GALLERY_IMAGES = ALL_TRIP_COVER_IMAGES.filter(
  (image) => !EXCLUDED_GALLERY_IMAGES.includes(image)
)