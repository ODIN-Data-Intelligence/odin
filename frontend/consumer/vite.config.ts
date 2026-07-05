import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

const DEV_HEADERS = {
  'X-API-Key': 'dev-local',
  'X-Tenant-Id': '00000000-0000-0000-0000-000000000001',
};

const AI_PROXY_TIMEOUT_MS = 360_000; // 6 minutes — matches AiServiceClient read timeout

function proxyEntry(target: string, timeoutMs?: number) {
  return {
    target,
    changeOrigin: true,
    ...(timeoutMs ? { proxyTimeout: timeoutMs, timeout: timeoutMs } : {}),
    configure: (proxy: import('http-proxy').Server) => {
      proxy.on('proxyReq', (proxyReq) => {
        Object.entries(DEV_HEADERS).forEach(([k, v]) => proxyReq.setHeader(k, v));
      });
    },
  };
}

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@datacatalog/shared': path.resolve(__dirname, '../shared/src/index.ts'),
    },
  },
  server: {
    port: 3001,
    proxy: {
      '/api/v1/search':          proxyEntry('http://localhost:8004'),
      '/api/v1/lineage':          proxyEntry('http://localhost:8003'),
      '/api/v1/ddl':             proxyEntry('http://localhost:8003'),
      '/api/v1/catalog-datasets': proxyEntry('http://localhost:8003'),
      '/api/v1/conversations':        proxyEntry('http://localhost:8005'),
      '/api/v1/semantic-search':      proxyEntry('http://localhost:8005'),
      '/api/v1/bookmark-collections': proxyEntry('http://localhost:8006'),
      '/api/v1/bookmarks':            proxyEntry('http://localhost:8006'),
      '/api/v1/users':                proxyEntry('http://localhost:8006'),
      '/api':                         proxyEntry('http://localhost:8001'),
    },
  },
});
