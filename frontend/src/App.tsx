import { BrowserRouter, Route, Routes } from 'react-router'
import { RequireAuth } from './auth/RequireAuth'
import { LandingPage } from './routes/LandingPage'
import { NotFoundPage } from './routes/NotFoundPage'
import { TripsPage } from './routes/TripsPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route
          path="/trips"
          element={
            <RequireAuth>
              <TripsPage />
            </RequireAuth>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
