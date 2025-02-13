import { BrowserRouter } from 'react-router-dom'
import { RootProvider } from '@/contexts/RootProvider'
import { AppRoutes } from '@/routes'
import '@/assets/App.css'

function App() {
  return (
    <BrowserRouter>
      <RootProvider>
        <AppRoutes />
      </RootProvider>
    </BrowserRouter>
  )
}

export default App