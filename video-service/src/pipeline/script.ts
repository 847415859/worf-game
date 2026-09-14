import { EventView, PlayerView, ReplayView, Scene } from '../types';
import { NARRATOR_VOICE, voiceForSeat } from './voice';

// 需要配音+文字卡片播出的"发言类"事件，见需求文档5.4/8.6节
const SPEECH_EVENT_TYPES = new Set(['speech', 'last_words', 'sheriff_campaign_speech', 'pk_speech']);
// 系统旁白类事件，走黑色字幕条+旁白音色
const CAPTION_EVENT_TYPES = new Set([
  'death_announcement',
  'vote_result',
  'sheriff_elected',
  'badge_transfer',
  'badge_destroyed',
  'skill_trigger',
  'settlement',
]);
// vote/sheriff_vote/pk_revote/night_action/role_assigned/inner_monologue 暂不生成画面，
// 太琐碎或涉及私密视角，见需求文档8.4节；后续要加只需把event_type挪进上面两个集合

// AI发言里常带emoji，中文字体没有对应字形会画出方块"豆腐块"，TTS也读不出来，
// 干脆在生成场景文本这一步就统一去掉，画面和配音都更干净
const EMOJI_PATTERN = /[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{2190}-\u{21FF}\u{2B00}-\u{2BFF}️]/gu;

function cleanText(text: string): string {
  return text.replace(EMOJI_PATTERN, '').replace(/[ \t]+/g, ' ').trim();
}

export function buildScript(replay: ReplayView): Scene[] {
  const playerBySeat = new Map<number, PlayerView>();
  const playerById = new Map<number, PlayerView>();
  for (const p of replay.players) {
    playerBySeat.set(p.seatNo, p);
    playerById.set(p.playerId, p);
  }

  const scenes: Scene[] = [];
  scenes.push(buildIntroScene(replay));

  for (const event of replay.events) {
    if (SPEECH_EVENT_TYPES.has(event.eventType)) {
      const actor = playerById.get(event.actorPlayerId);
      if (!actor || !event.content) continue;
      scenes.push({
        index: scenes.length,
        kind: 'speech',
        day: event.day,
        phase: event.phase,
        seatNo: actor.seatNo,
        speakerName: actor.displayName,
        claimedRole: event.claimedRole,
        text: cleanText(event.content),
        voice: voiceForSeat(actor.seatNo),
      });
    } else if (CAPTION_EVENT_TYPES.has(event.eventType)) {
      if (!event.content) continue;
      scenes.push({
        index: scenes.length,
        kind: 'caption',
        day: event.day,
        phase: event.phase,
        seatNo: null,
        speakerName: null,
        claimedRole: null,
        text: cleanText(event.content),
        voice: NARRATOR_VOICE,
      });
    }
  }
  return scenes;
}

function buildIntroScene(replay: ReplayView): Scene {
  const roster = [...replay.players]
    .sort((a, b) => a.seatNo - b.seatNo)
    .map((p) => `${p.seatNo}号${p.displayName}(${roleLabel(p.inGameRole)})`)
    .join('，');
  return {
    index: 0,
    kind: 'caption',
    day: 0,
    phase: 'intro',
    seatNo: null,
    speakerName: null,
    claimedRole: null,
    text: `本局座位与真实身份：${roster}。`,
    voice: NARRATOR_VOICE,
  };
}

function roleLabel(roleKey: string | null): string {
  const map: Record<string, string> = {
    werewolf: '狼人',
    seer: '预言家',
    witch: '女巫',
    hunter: '猎人',
    villager: '平民',
  };
  return roleKey ? map[roleKey] || roleKey : '未知';
}
