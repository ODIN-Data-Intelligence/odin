import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

const DEV_HEADERS = {
  'X-API-Key': 'dev-local',
  'X-Tenant-Id': '00000000-0000-0000-0000-000000000001',
};

function proxyEntry(target: string) {
  return {
    target,
    changeOrigin: true,
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
    port: 3000,
    proxy: {
      '/api/v1/search':          proxyEntry('http://localhost:8004'),
      '/api/v1/lineage':          proxyEntry('http://localhost:8003'),
      '/api/v1/ddl':             proxyEntry('http://localhost:8003'),
      '/api/v1/catalog-datasets': proxyEntry('http://localhost:8003'),
      '/api/v1/conversations':   proxyEntry('http://localhost:8005'),
      '/api/v1/semantic-search': proxyEntry('http://localhost:8005'),
      '/api':                    proxyEntry('http://localhost:8001'),
    },
  },
});
