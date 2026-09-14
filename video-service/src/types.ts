// 与后端 com.wolf.backend.dto.ReplayView / EventView / PlayerView 保持字段一致（Jackson默认camelCase）

export interface PlayerView {
  playerId: number;
  seatNo: number;
  ai: boolean;
  alive: boolean;
  displayName: string;
  avatarUrl: string | null;
  inGameRole: string | null;
  camp: string | null;
  eliminatedDay: number | null;
  eliminatedReason: string | null;
  sheriff: boolean;
}

export interface EventView {
  id: number;
  day: number;
  phase: string;
  actorPlayerId: number;
  actorName: string | null;
  eventType: string;
  targetPlayerId: number | null;
  targetName: string | null;
  claimedRole: string | null;
  content: string | null;
  visibleScope: string;
  createdAt: string | null;
}

export interface ReplayView {
  sessionId: number;
  roomId: number;
  dayCount: number;
  winnerCamp: string | null;
  players: PlayerView[];
  events: EventView[];
}

export type SceneKind = 'caption' | 'speech';

export interface VoiceProfile {
  voice: string;
  pitch: string;
  rate: string;
}

export interface Scene {
  index: number;
  kind: SceneKind;
  day: number;
  phase: string;
  seatNo: number | null;
  speakerName: string | null;
  claimedRole: string | null;
  text: string;
  voice: VoiceProfile;
}
