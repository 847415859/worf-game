const API_BASE = 'http://localhost:8080';
export const WS_BASE = 'ws://localhost:8080';
const VIDEO_SERVICE_BASE = 'http://localhost:3200';

async function request(path, options = {}) {
  const resp = await fetch(API_BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!resp.ok) {
    let message = resp.statusText;
    try {
      const body = await resp.json();
      message = body.error || message;
    } catch (e) {
      // ignore
    }
    throw new Error(message);
  }
  const text = await resp.text();
  return text ? JSON.parse(text) : null;
}

export const api = {
  listCharacters: () => request('/api/characters'),
  listRoleDefinitions: () => request('/api/role-definitions'),
  listRoomTemplates: () => request('/api/room-templates'),
  createRoom: (payload) => request('/api/rooms', { method: 'POST', body: JSON.stringify(payload) }),
  getRoom: (roomId) => request(`/api/rooms/${roomId}`),
  getRoomSession: (roomId) => request(`/api/rooms/${roomId}/session`),
  joinRoom: (roomId, payload) => request(`/api/rooms/${roomId}/join`, { method: 'POST', body: JSON.stringify(payload) }),
  startRoom: (roomId) => request(`/api/rooms/${roomId}/start`, { method: 'POST' }),
  getState: (sessionId, playerId, godView) => {
    const params = new URLSearchParams();
    if (playerId) params.set('playerId', playerId);
    if (godView) params.set('godView', 'true');
    const qs = params.toString();
    return request(`/api/sessions/${sessionId}/state${qs ? '?' + qs : ''}`);
  },
  submitAction: (sessionId, payload) => request(`/api/sessions/${sessionId}/actions`, { method: 'POST', body: JSON.stringify(payload) }),
  pauseSession: (sessionId) => request(`/api/sessions/${sessionId}/pause`, { method: 'POST' }),
  resumeSession: (sessionId) => request(`/api/sessions/${sessionId}/resume`, { method: 'POST' }),
  endSession: (sessionId) => request(`/api/sessions/${sessionId}/end`, { method: 'POST' }),

  generateVideo: (sessionId) => requestVideo(`/videos/${sessionId}/generate`, { method: 'POST' }),
  getVideoStatus: (jobId) => requestVideo(`/videos/${jobId}/status`),
  videoDownloadUrl: (jobId) => `${VIDEO_SERVICE_BASE}/videos/${jobId}/download`,
};

async function requestVideo(path, options = {}) {
  const resp = await fetch(VIDEO_SERVICE_BASE + path, options);
  if (!resp.ok) {
    let message = resp.statusText;
    try {
      const body = await resp.json();
      message = body.error || message;
    } catch (e) {
      // ignore
    }
    throw new Error(message);
  }
  return resp.json();
}
