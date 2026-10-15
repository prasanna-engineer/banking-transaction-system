import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    // Must match the origin the backend allows in CORS_ALLOWED_ORIGINS.
    port: 5173,
    strictPort: true,
  },
})
