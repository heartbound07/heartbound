import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react-swc'
import path from 'path'

export default defineConfig(({ mode, command }) => {
  // Load environment variables for the current mode.
  const env = loadEnv(mode, process.cwd(), '')
  // Use VITE_API_URL for API target in development, defaulting to localhost if not set.
  const apiUrl = env.VITE_API_URL || 'http://localhost:8080'
  // Define a base URL for production if needed (default is '/')
  const baseUrl = env.VITE_BASE_URL || '/'

  return {
    base: baseUrl,
    plugins: [react()],
    server: {
      port: 3000,
      proxy: {
        '/api': {
          target: apiUrl,
          changeOrigin: true,
          // Optionally strip the "/api" prefix when forwarding:
          // rewrite: (path) => path.replace(/^\/api/, '')
        }
      }
    },
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src')
      }
    },
    define: {
      global: 'window',
    },
  }
})