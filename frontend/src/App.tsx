import { BrowserRouter } from 'react-router-dom'
import { AuthProvider } from '@/contexts/auth'
import { AppRoutes } from '@/routes'
import '@/assets/App.css'

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App