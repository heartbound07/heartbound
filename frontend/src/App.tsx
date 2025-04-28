import { BrowserRouter } from 'react-router-dom'
import { RootProvider } from '@/contexts/RootProvider'
import { AppRoutes } from '@/routes'
import '@/assets/App.css'
import '@/assets/theme.css'

function App() {
  return (
    <BrowserRouter
      future={{
        v7_startTransition: true,
        v7_relativeSplatPath: true
      }}
    >
      <RootProvider>
        <AppRoutes />
      </RootProvider>
    </BrowserRouter>
  )
}

export default App