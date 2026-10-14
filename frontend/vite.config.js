import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Forward /api/* to Spring Boot, so the browser sees one origin and no CORS setup is needed.
    proxy: {
      '/api': 'http://localhost:8081',
    },
  },
})
