import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// 开发时代理到同一平台，正式产物由平台同源提供，避免跨源存放身份令牌。
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': 'http://localhost:18080',
      '/login': 'http://localhost:18080',
      '/logout': 'http://localhost:18080',
    },
  },
  build: { outDir: 'dist' },
});
