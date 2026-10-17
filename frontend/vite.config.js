
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],

  server: {
    port: 5173,

    proxy: {
      // Registro, inicio de sesion y perfil
      '/api/v1/auth': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },

      // Publicaciones y comentarios
      '/api/v1/posts': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
    },
  },
})
