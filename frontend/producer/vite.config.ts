import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

const DEV_HEADERS = {
  'X-API-Key': 'dev-local',
  'X-Tenant-Id': '00000000-0000-0000-0000-000000000001',
};

const AI_PROXY_TIMEOUT_MS = 600_000; // 10 minutes — headroom above ai-service 5-min LLM timeout

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
    port: 3000,
    proxy: {
      '/api/v1/search':               proxyEntry('http://localhost:8004'),
      '/api/v1/lineage':              proxyEntry('http://localhost:8003'),
      '/api/v1/ddl':                  proxyEntry('http://localhost:8003'),
      '/api/v1/catalog-datasets':     proxyEntry('http://localhost:8003'),
      '/api/v1/conversations':        proxyEntry('http://localhost:8005'),
      '/api/v1/agentic-review':       proxyEntry('http://localhost:8005', AI_PROXY_TIMEOUT_MS),
      '/api/v1/semantic-search':      proxyEntry('http://localhost:8005'),
      '/api/v1/sources':              proxyEntry('http://localhost:8002'),
      '/api/v1/jobs':                 proxyEntry('http://localhost:8002'),
      '/api/v1/runs':                 proxyEntry('http://localhost:8002'),
      // AI-backed endpoints on inventory-service need a long proxy timeout
      '/api/v1/policies':             proxyEntry('http://localhost:8007'),
      '/api/v1/logical-models':       proxyEntry('http://localhost:8001', AI_PROXY_TIMEOUT_MS),
      '/api/v1/logical-data-elements': proxyEntry('http://localhost:8001', AI_PROXY_TIMEOUT_MS),
      '/api':                         proxyEntry('http://localhost:8001'),
    },
  },
});
