import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/auth': 'http://localhost:8080',
      '/api': 'http://localhost:8080',
      '/identity': 'http://localhost:8080',
      '/course-api': {
        target: 'http://localhost:8081',
        rewrite: (p) => p.replace(/^\/course-api/, ''),
      },
    },
  },
});
