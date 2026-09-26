import type { SignIn } from '@clerk/react'
import type { ComponentProps } from 'react'

/**
 * Clerk's appearance type for v6 is not re-exported by name from `@clerk/react`
 * (it lives in `@clerk/shared` behind a global registry), so we derive it from
 * the `SignIn` component's own prop type. This keeps the object in sync with the
 * installed Clerk version and avoids depending on a transitive package path.
 */
type ClerkAppearance = NonNullable<ComponentProps<typeof SignIn>['appearance']>

/**
 * Dark editorial theme for Clerk's hosted auth components, matching the app
 * tokens in `index.css` (navy surfaces, cream text, amber primary, Outfit body,
 * Fraunces headings, rounded-xl controls).
 *
 * Values are hex/rgba here on purpose: Clerk renders outside our Tailwind theme
 * and needs literal CSS values. Everything else in the app consumes tokens.
 */
export const clerkAppearance: ClerkAppearance = {
  variables: {
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
    fontFamily: "'Outfit', system-ui, sans-serif",
    fontFamilyButtons: "'Outfit', system-ui, sans-serif",
    fontFamilyMono: "'JetBrains Mono', monospace",
    borderRadius: '0.75rem',
    spacing: '1rem',
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
      fontFamily: "'Fraunces', Georgia, serif",
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
