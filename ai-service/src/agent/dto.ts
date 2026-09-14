// 与后端 com.wolf.backend.ai.AiDecideRequest / AiDecideResponse 保持字段一致的内部契约
// 见需求文档8.1节 System Prompt组装结构、8.3节两段式决策链

export interface CharacterInfo {
  characterId: number;
  name: string;
  backstory: string | null;
  personalityVector: Record<string, number> | null;
  speechStyle: Record<string, unknown> | null;
}

export interface SelfInfo {
  playerId: number;
  seatNo: number;
  inGameRole: string;
  camp: string;
  sheriff: boolean;
  alive: boolean;
}

export interface PlayerInfo {
  playerId: number;
  seatNo: number;
  displayName: string;
  alive: boolean;
  sheriff: boolean;
  knownCamp: string | null;
}

export interface EventWindowItem {
  day: number;
  phase: string;
  actorName: string;
  eventType: string;
  claimedRole: string | null;
  targetName: string | null;
  content: string | null;
}

export interface GameContext {
  sessionId: number;
  day: number;
  phase: string;
  self: SelfInfo;
  players: PlayerInfo[];
  eventWindow: EventWindowItem[];
}

export interface CandidateTarget {
  playerId: number;
  displayName: string;
}

export interface Task {
  actionType: string;
  instruction: string;
  candidateTargets: CandidateTarget[];
  allowAbstain: boolean;
}

export interface AiDecideRequest {
  character: CharacterInfo;
  gameContext: GameContext;
  task: Task;
}

export interface AiDecideResponse {
  content: string | null;
  targetPlayerId: number | null;
  claimedRole: string | null;
  decisionYes: boolean | null;
  innerMonologue: string | null;
}
