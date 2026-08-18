/** "n분 전" style relative time for observation timestamps (null-safe). */
export function timeAgo(iso: string | null | undefined): string | null {
  if (!iso) {
    return null;
  }
  const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) {
    return '방금 전';
  }
  if (s < 3600) {
    return `${Math.floor(s / 60)}분 전`;
  }
  if (s < 86400) {
    return `${Math.floor(s / 3600)}시간 전`;
  }
  return `${Math.floor(s / 86400)}일 전`;
}
