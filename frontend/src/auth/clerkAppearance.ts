import type { SignIn } from '@clerk/react'
import type { ComponentProps } from 'react'
import type { Theme } from '../lib/theme'

/**
 * Clerk's appearance type for v6 is not re-exported by name from `@clerk/react`
 * (it lives in `@clerk/shared` behind a global registry), so we derive it from
 * the `SignIn` component's own prop type. This keeps the object in sync with the
 * installed Clerk version and avoids depending on a transitive package path.
 */
type ClerkAppearance = NonNullable<ComponentProps<typeof SignIn>['appearance']>

const DISPLAY_FONT = "'Fraunces', Georgia, serif"
const BODY_FONT = "'Outfit', system-ui, sans-serif"
const MONO_FONT = "'JetBrains Mono', monospace"

const SHARED_VARIABLES = {
  fontFamily: BODY_FONT,
  fontFamilyButtons: BODY_FONT,
  fontFamilyMono: MONO_FONT,
  borderRadius: '0.75rem',
  spacing: '1rem',
}

/*
 * Values below are literal hex/rgba on purpose: Clerk renders its own component
 * tree, outside our Tailwind theme, so it needs concrete CSS values. They mirror
 * the semantic tokens in `index.css` for each theme.
 */

export const clerkAppearanceDark: ClerkAppearance = {
  variables: {
    ...SHARED_VARIABLES,
    colorPrimary: '#d4882a',
    colorPrimaryForeground: '#0d1b2a',
    colorBackground: '#162032',
    colorForeground: '#f5efe6',
    colorMuted: 'rgba(255, 255, 255, 0.04)',
    colorMutedForeground: '#d6ccbf',
    colorInput: 'rgba(255, 255, 255, 0.05)',
    colorInputForeground: '#f5efe6',
    colorNeutral: 'white',
    colorBorder: 'rgba(255, 255, 255, 0.10)',
    colorRing: 'rgba(212, 136, 42, 0.5)',
    colorShadow: '#000000',
    colorShimmer: 'rgba(255, 255, 255, 0.06)',
    colorDanger: '#c15a3f',
    colorSuccess: '#5c8a6e',
    colorWarning: '#e8a84c',
    colorModalBackdrop: 'rgba(13, 27, 42, 0.78)',
  },
  elements: {
    rootBox: { width: '100%' },
    card: {
      backgroundColor: '#162032',
      border: '1px solid rgba(255, 255, 255, 0.08)',
      borderRadius: '1rem',
      boxShadow: '0 30px 80px -40px rgba(0, 0, 0, 0.9)',
    },
    headerTitle: {
      fontFamily: DISPLAY_FONT,
      fontWeight: 500,
      letterSpacing: '-0.01em',
      color: '#f5efe6',
    },
    headerSubtitle: { color: '#d6ccbf' },
    socialButtonsBlockButton: {
      backgroundColor: 'rgba(255, 255, 255, 0.03)',
      border: '1px solid rgba(255, 255, 255, 0.10)',
      color: '#f5efe6',
      '&:hover': { backgroundColor: 'rgba(255, 255, 255, 0.06)' },
    },
    socialButtonsBlockButtonText: { color: '#f5efe6', fontWeight: 500 },
    dividerLine: { backgroundColor: 'rgba(255, 255, 255, 0.08)' },
    dividerText: { color: 'rgba(214, 204, 191, 0.6)' },
    formFieldLabel: { color: '#d6ccbf', fontWeight: 500 },
    formFieldInput: {
      backgroundColor: 'rgba(255, 255, 255, 0.05)',
      border: '1px solid rgba(255, 255, 255, 0.10)',
      color: '#f5efe6',
      borderRadius: '0.75rem',
      '&:focus': {
        borderColor: 'rgba(212, 136, 42, 0.5)',
        boxShadow: '0 0 0 1px rgba(212, 136, 42, 0.4)',
      },
    },
    formButtonPrimary: {
      backgroundColor: '#d4882a',
      color: '#0d1b2a',
      fontWeight: 600,
      borderRadius: '0.75rem',
      textTransform: 'none',
      '&:hover': { backgroundColor: '#e8a84c' },
      '&:focus': { boxShadow: '0 0 0 2px rgba(232, 168, 76, 0.6)' },
    },
    footerActionText: { color: '#d6ccbf' },
    footerActionLink: {
      color: '#e8a84c',
      fontWeight: 500,
      '&:hover': { color: '#f5efe6' },
    },
    identityPreviewText: { color: '#f5efe6' },
    identityPreviewEditButton: {
      color: '#e8a84c',
      '&:hover': { color: '#f5efe6' },
    },
    formResendCodeLink: {
      color: '#e8a84c',
      '&:hover': { color: '#f5efe6' },
    },
    alertText: { color: '#d6ccbf' },
    otpCodeFieldInput: {
      backgroundColor: 'rgba(255, 255, 255, 0.05)',
      border: '1px solid rgba(255, 255, 255, 0.10)',
      color: '#f5efe6',
    },
  },
}

export const clerkAppearanceLight: ClerkAppearance = {
  variables: {
    ...SHARED_VARIABLES,
    colorPrimary: '#d4882a',
    colorPrimaryForeground: '#0d1b2a',
    colorBackground: '#ffffff',
    colorForeground: '#182430',
    colorMuted: 'rgba(13, 27, 42, 0.04)',
    colorMutedForeground: '#4f5b69',
    colorInput: 'rgba(13, 27, 42, 0.03)',
    colorInputForeground: '#182430',
    colorNeutral: 'black',
    colorBorder: 'rgba(13, 27, 42, 0.12)',
    colorRing: 'rgba(163, 95, 14, 0.45)',
    colorShadow: '#1b2733',
    colorShimmer: 'rgba(13, 27, 42, 0.06)',
    colorDanger: '#b23c22',
    colorSuccess: '#3f6b50',
    colorWarning: '#a35f0e',
    colorModalBackdrop: 'rgba(20, 27, 36, 0.55)',
  },
  elements: {
    rootBox: { width: '100%' },
    card: {
      backgroundColor: '#ffffff',
      border: '1px solid rgba(13, 27, 42, 0.10)',
      borderRadius: '1rem',
      boxShadow: '0 24px 60px -34px rgba(16, 27, 42, 0.4)',
    },
    headerTitle: {
      fontFamily: DISPLAY_FONT,
      fontWeight: 500,
      letterSpacing: '-0.01em',
      color: '#182430',
    },
    headerSubtitle: { color: '#4f5b69' },
    socialButtonsBlockButton: {
      backgroundColor: '#ffffff',
      border: '1px solid rgba(13, 27, 42, 0.12)',
      color: '#182430',
      '&:hover': { backgroundColor: 'rgba(13, 27, 42, 0.04)' },
    },
    socialButtonsBlockButtonText: { color: '#182430', fontWeight: 500 },
    dividerLine: { backgroundColor: 'rgba(13, 27, 42, 0.10)' },
    dividerText: { color: '#7a8494' },
    formFieldLabel: { color: '#3b4552', fontWeight: 500 },
    formFieldInput: {
      backgroundColor: 'rgba(13, 27, 42, 0.03)',
      border: '1px solid rgba(13, 27, 42, 0.12)',
      color: '#182430',
      borderRadius: '0.75rem',
      '&:focus': {
        borderColor: 'rgba(163, 95, 14, 0.5)',
        boxShadow: '0 0 0 1px rgba(163, 95, 14, 0.35)',
      },
    },
    formButtonPrimary: {
      backgroundColor: '#d4882a',
      color: '#0d1b2a',
      fontWeight: 600,
      borderRadius: '0.75rem',
      textTransform: 'none',
      '&:hover': { backgroundColor: '#c0761c' },
      '&:focus': { boxShadow: '0 0 0 2px rgba(163, 95, 14, 0.5)' },
    },
    footerActionText: { color: '#4f5b69' },
    footerActionLink: {
      color: '#a35f0e',
      fontWeight: 500,
      '&:hover': { color: '#7d4a0b' },
    },
    identityPreviewText: { color: '#182430' },
    identityPreviewEditButton: {
      color: '#a35f0e',
      '&:hover': { color: '#7d4a0b' },
    },
    formResendCodeLink: {
      color: '#a35f0e',
      '&:hover': { color: '#7d4a0b' },
    },
    alertText: { color: '#3b4552' },
    otpCodeFieldInput: {
      backgroundColor: 'rgba(13, 27, 42, 0.03)',
      border: '1px solid rgba(13, 27, 42, 0.12)',
      color: '#182430',
    },
  },
}

/** Returns the Clerk appearance for the given app theme. */
export function getClerkAppearance(theme: Theme): ClerkAppearance {
  return theme === 'light' ? clerkAppearanceLight : clerkAppearanceDark
}

/** @deprecated Use `getClerkAppearance(theme)`; kept as the dark default. */
export const clerkAppearance = clerkAppearanceDark
