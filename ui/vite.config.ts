import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import fs from 'fs';

const useMock = process.env.VITE_MOCK === 'true';

function mockPlugin() {
  // Track whether we've already sent the initial batch of messages
  let messagesSent = false;

  return {
    name: 'mock-api',
    configureServer(server: import('vite').ViteDevServer) {
      server.middlewares.use((req, res, next) => {
        const mockDir = path.resolve(__dirname, 'mock');

        if (req.url?.startsWith('/camelbee/routes')) {
          res.setHeader('Content-Type', 'application/json');
          res.end(fs.readFileSync(path.join(mockDir, 'routes.json'), 'utf-8'));
          return;
        }

        if (req.url?.startsWith('/camelbee/messages')) {
          if (req.method === 'DELETE') {
            messagesSent = false;
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify('OK'));
            return;
          }
          res.setHeader('Content-Type', 'application/json');
          if (!messagesSent) {
            // First poll: return the full mock data
            messagesSent = true;
            res.end(fs.readFileSync(path.join(mockDir, 'messages.json'), 'utf-8'));
          } else {
            // Subsequent polls: return empty (no new messages)
            res.end(JSON.stringify({
              messages: [],
              info: { count: 136, resetVersion: 0, addVersion: 1, lastModified: "1699197000505", lastResetTime: "1699195800000" }
            }));
          }
          return;
        }

        if (req.url?.startsWith('/camelbee/tracer/status')) {
          res.setHeader('Content-Type', 'application/json');
          res.end(JSON.stringify('OK'));
          return;
        }

        if (req.url?.startsWith('/camelbee/produce/direct')) {
          res.setHeader('Content-Type', 'application/json');
          res.end(JSON.stringify('{"status":"ok","message":"Mock route call executed"}'));
          return;
        }

        if (req.url?.startsWith('/health') || req.url?.startsWith('/q/health') || req.url?.startsWith('/actuator/health')) {
          res.setHeader('Content-Type', 'application/json');
          res.end(fs.readFileSync(path.join(mockDir, 'health.json'), 'utf-8'));
          return;
        }

        if (req.url?.startsWith('/metrics') || req.url?.startsWith('/q/metrics') || req.url?.startsWith('/actuator/metrics')) {
          res.setHeader('Content-Type', 'text/plain');
          res.end(fs.readFileSync(path.join(mockDir, 'metrics.txt'), 'utf-8'));
          return;
        }

        next();
      });
    },
  };
}

export default defineConfig({
  plugins: [react(), ...(useMock ? [mockPlugin()] : [])],
  base: '/camelbee/',
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
  server: {
    ...(!useMock && {
      proxy: {
        '/camelbee/routes': 'http://localhost:8080',
        '/camelbee/messages': 'http://localhost:8080',
        '/camelbee/tracer': 'http://localhost:8080',
        '/camelbee/produce': 'http://localhost:8080',
        '/health': 'http://localhost:8080',
        '/q/health': 'http://localhost:8080',
        '/metrics': 'http://localhost:8080',
        '/q/metrics': 'http://localhost:8080',
        '/actuator': 'http://localhost:8080',
      },
    }),
  },
});
