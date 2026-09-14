import { ReplayView } from '../types';

const BACKEND_BASE_URL = process.env.BACKEND_BASE_URL || 'http://127.0.0.1:8080';

export async function fetchReplay(sessionId: number): Promise<ReplayView> {
  const resp = await fetch(`${BACKEND_BASE_URL}/api/sessions/${sessionId}/replay`);
  if (!resp.ok) {
    const body = await resp.text().catch(() => '');
    throw new Error(`获取对局回放失败(${resp.status}): ${body}`);
  }
  return (await resp.json()) as ReplayView;
}
