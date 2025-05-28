import { BrowserRouter } from 'react-router-dom'
import { RootProvider } from '@/contexts/RootProvider'
import { AppRoutes } from '@/routes'
import '@/assets/App.css'
import '@/assets/theme.css'
import QueueUpdatesProvider from '@/contexts/QueueUpdates'

function App() {
  return (
    <BrowserRouter
      future={{
        v7_startTransition: true,
        v7_relativeSplatPath: true
      }}
    >
      <RootProvider>
        <QueueUpdatesProvider>
          <AppRoutes />
        </QueueUpdatesProvider>
      </RootProvider>
    </BrowserRouter>
  )
}

export default App