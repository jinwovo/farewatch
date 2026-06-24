import './globals.css';
import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import Link from 'next/link';

export const metadata: Metadata = {
  title: 'farewatch — 항공권 최저가 감시',
  description: '원하는 날짜·도시 항공권 최저가를 매시간 추적 → 갱신 시 알림 → 최저가 사이트로 이동',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <div className="promo">
          ✈ 여행 <b>한 달 전</b>, 최저가가 갱신되면 바로 알려드려요 — 추적 → 알림 → 최저가 사이트로
        </div>
        <header className="topbar">
          <Link href="/" className="brand">
            fare<span className="dot">·</span>watch
          </Link>
          <span className="nav-tag">항공권 최저가 감시</span>
          <span className="nav-spacer" />
        </header>
        <main className="container">{children}</main>
      </body>
    </html>
  );
}
