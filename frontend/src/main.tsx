import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App'
import { AuthProvider } from './contexts/auth'
import { PartyUpdatesProvider } from './contexts/PartyUpdates'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider>
      <PartyUpdatesProvider>
        <App />
      </PartyUpdatesProvider>
    </AuthProvider>
  </StrictMode>,
)
