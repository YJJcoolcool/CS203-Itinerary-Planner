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

        <div className="mt-10 flex flex-col gap-4 sm:flex-row">
          <button
            type="button"
            className="rounded-lg bg-gray-900 px-6 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-gray-700"
          >
            Sign In
          </button>

          <button
            type="button"
            className="rounded-lg border border-gray-300 bg-white px-6 py-3 text-sm font-semibold text-gray-900 shadow-sm transition hover:bg-gray-50"
          >
            Create Account
          </button>
        </div>

      </div>
    </main>
  )
}

export default App