import { useAuth } from '@clerk/react'
import { Navigate } from 'react-router'
import { Navbar } from '../components/Navbar'
import { useAuthConfiguration } from '../auth/authConfiguration'

function LandingContent() {
  return (
    <main className="min-h-screen bg-white">
      <Navbar variant="light" />

      <div className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-5xl flex-col items-center justify-center px-6 text-center">
        <h1 className="text-5xl font-bold tracking-tight text-gray-900 sm:text-6xl">
          Agile Trips
        </h1>

        <p className="mt-6 max-w-2xl text-lg leading-8 text-gray-600">
          Plan your trips, organise your itinerary, and adapt your plans
          when things change.
        </p>
      </div>
    </main>
  )
}

function RedirectWhenSignedIn() {
  const { isLoaded, isSignedIn } = useAuth()

  if (!isLoaded) {
    return null
  }

  if (isSignedIn) {
    return <Navigate to="/trips" replace />
  }

  return <LandingContent />
}

export function LandingPage() {
  const { isConfigured } = useAuthConfiguration()

  if (!isConfigured) {
    return <LandingContent />
  }

  return <RedirectWhenSignedIn />
}
