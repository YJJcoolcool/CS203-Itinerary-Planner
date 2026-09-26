import { Show, SignInButton, SignUpButton, UserButton } from '@clerk/react'
import { useAuthConfiguration } from './auth/authConfiguration'

function HomepageAuthActions() {
  const { isConfigured } = useAuthConfiguration()

  if (!isConfigured) {
    return (
      <p className="mt-10 text-sm text-gray-600" role="status">
        Authentication will be available after Clerk is configured.
      </p>
    )
  }

  return (
    <div className="mt-10 flex flex-col items-center gap-4 sm:flex-row">
      <Show when="signed-out">
        <SignInButton mode="modal">
          <button
            type="button"
            className="rounded-lg bg-gray-900 px-6 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-gray-700"
          >
            Sign In
          </button>
        </SignInButton>
        <SignUpButton mode="modal">
          <button
            type="button"
            className="rounded-lg border border-gray-300 bg-white px-6 py-3 text-sm font-semibold text-gray-900 shadow-sm transition hover:bg-gray-50"
          >
            Create Account
          </button>
        </SignUpButton>
      </Show>
      <Show when="signed-in">
        <span className="text-sm font-medium text-gray-700">Signed in</span>
        <UserButton />
      </Show>
    </div>
  )
}

function App() {
  return (
    <main className="min-h-screen bg-white">
      <div className="mx-auto flex min-h-screen max-w-5xl flex-col items-center justify-center px-6 text-center">
        <h1 className="text-5xl font-bold tracking-tight text-gray-900 sm:text-6xl">
          Agile Trips
        </h1>

        <p className="mt-6 max-w-2xl text-lg leading-8 text-gray-600">
          Plan your trips, organise your itinerary, and adapt your plans
          when things change.
        </p>

        <HomepageAuthActions />
      </div>
    </main>
  )
}

export default App
