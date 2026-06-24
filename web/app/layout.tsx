import './globals.css';
import type { Metadata } from 'next';
import type { ReactNode } from 'react';

export const metadata: Metadata = {
  title: 'farewatch',
  description: '원하는 날짜·도시 항공권 최저가 감시 → 갱신 시 알림 → 최저가 사이트로 이동',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <header className="topbar">
          <span className="brand">✈ farewatch</span>
          <span className="tag">항공권 최저가 감시</span>
        </header>
        <main className="container">{children}</main>
      </body>
    </html>
  );
}
