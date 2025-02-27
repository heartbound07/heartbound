import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { PartyUpdatesProvider } from './contexts/PartyUpdates'
import { AuthProvider } from './contexts/auth/AuthProvider'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider>
      <PartyUpdatesProvider>
        <App />
      </PartyUpdatesProvider>
    </AuthProvider>
  </StrictMode>,
)
