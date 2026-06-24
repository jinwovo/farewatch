/** @type {import('next').NextConfig} */

// Proxy /api/* to the Spring backend so the browser stays same-origin (no CORS).
const BACKEND_ORIGIN = process.env.BACKEND_ORIGIN || 'http://localhost:8101';

const nextConfig = {
  eslint: { ignoreDuringBuilds: true },
  async rewrites() {
    return [{ source: '/api/:path*', destination: `${BACKEND_ORIGIN}/api/:path*` }];
  },
};

export default nextConfig;
