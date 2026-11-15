import express from 'express';
import { createServer as createViteServer } from 'vite';
import path from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';
import { initDatabase, getDbStatus, getLocalLanIp } from './server/db';
import { apiRouter } from './server/routes';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function startServer() {
  const app = express();
  const PORT = process.env.PORT ? parseInt(process.env.PORT, 10) : 3000;

  // Support local HTTPS reverse tunnels (Cloudflare Tunnel / ngrok)
  app.set('trust proxy', 1);

  // Body Parser with generous limit for resumes / profiles
  app.use(express.json({ limit: '10mb' }));
  app.use(express.urlencoded({ extended: true, limit: '10mb' }));

  // CORS configuration supporting configured ALLOWED_ORIGINS, localhost, and Google AI Studio Web origins
  const defaultAiStudioOrigins = [
    'https://ais-dev-htjupslzh2ixgjtaq6uldb-22462661599.asia-east1.run.app',
    'https://ais-pre-htjupslzh2ixgjtaq6uldb-22462661599.asia-east1.run.app',
    'https://aistudio.google.com',
  ];
  const rawAllowedOrigins = (process.env.ALLOWED_ORIGINS || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);

  const isOriginAllowed = (origin: string | undefined): boolean => {
    if (!origin) return true;
    if (rawAllowedOrigins.includes('*')) return true;
    if (rawAllowedOrigins.includes(origin) || defaultAiStudioOrigins.includes(origin)) return true;
    if (process.env.APP_URL && origin === process.env.APP_URL) return true;
    try {
      const parsed = new URL(origin);
      if (parsed.hostname === 'localhost' || parsed.hostname === '127.0.0.1') {
        return true;
      }
      if (
        parsed.hostname === 'aistudio.google.com' ||
        parsed.hostname.endsWith('.aistudio.google.com') ||
        parsed.hostname.endsWith('.googleusercontent.com') ||
        parsed.hostname.endsWith('.run.app')
      ) {
        return true;
      }
    } catch {
      return false;
    }
    return false;
  };

  app.use((req, res, next) => {
    const origin = req.headers.origin;
    if (origin && isOriginAllowed(origin)) {
      res.header('Access-Control-Allow-Origin', origin);
      res.header('Vary', 'Origin');
    } else if (!origin && rawAllowedOrigins.includes('*')) {
      res.header('Access-Control-Allow-Origin', '*');
    }
    res.header(
      'Access-Control-Allow-Headers',
      'Origin, X-Requested-With, Content-Type, Accept, Authorization'
    );
    res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, PATCH, DELETE, OPTIONS');
    if (req.method === 'OPTIONS') {
      if (origin && !isOriginAllowed(origin)) {
        return res.status(403).json({ error: 'Origin not allowed by CORS policy.' });
      }
      return res.sendStatus(200);
    }
    next();
  });

  // Attempt initial MySQL connection and non-destructive schema migrations
  console.log('[GCET Placement Hub] Bootstrapping backend and connecting to MySQL...');
  await initDatabase().catch((err) => {
    console.warn('[GCET Placement Hub] Initial MySQL connection note:', err.message);
  });

  // Mount API router
  app.use('/api', apiRouter);

  // Mount Vite development middlewares or serve static production build
  if (process.env.NODE_ENV === 'production') {
    app.use(express.static(path.resolve(__dirname, 'dist')));
    app.get('*', (req, res) => {
      res.sendFile(path.resolve(__dirname, 'dist', 'index.html'));
    });
  } else {
    const vite = await createViteServer({
      server: {
        middlewareMode: true,
        host: '0.0.0.0',
        port: PORT,
      },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  }

  app.listen(PORT, '0.0.0.0', () => {
    const lanIp = getLocalLanIp();
    const dbStatus = getDbStatus();
    console.log('================================================================');
    console.log(`GCET PLACEMENT HUB - Full-Stack Application Running`);
    console.log(`Local Access URL:   http://localhost:${PORT}`);
    console.log(`LAN Access URL:     http://${lanIp}:${PORT}`);
    console.log(`MySQL Central DB:   ${dbStatus.database} (${dbStatus.host}:${dbStatus.port})`);
    console.log(`MySQL Status:       ${dbStatus.connected ? 'CONNECTED' : 'DISCONNECTED'}`);
    console.log('================================================================');
  });
}

startServer().catch((err) => {
  console.error('[GCET Placement Hub] Fatal error starting server:', err);
  process.exit(1);
});
