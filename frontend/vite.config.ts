import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 开发期把 /api 代理到后端，避免跨域，也让会话 Cookie 与 CSRF 双提交在同源下正常工作
export default defineConfig({
  plugins: [react()],
  resolve: {
    // 与 tsconfig.json 的 paths 保持一致，两边都要改
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    // antd 整包约 900 KB（gzip 290 KB）是已知且可接受的体量，调高阈值避免每次构建都刷警告。
    // 真要再降只能按需引入或路由级懒加载，S2 阶段收益不大。
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        // antd 体积大且很少变动，单独成块后业务代码更新不会让用户重下整个依赖
        manualChunks: {
          antd: ['antd', '@ant-design/icons'],
          react: ['react', 'react-dom', 'react-router-dom'],
          query: ['@tanstack/react-query'],
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
