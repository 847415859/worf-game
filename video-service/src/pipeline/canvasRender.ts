import { createCanvas, GlobalFonts, SKRSContext2D } from '@napi-rs/canvas';
import * as fs from 'fs';
import * as path from 'path';
import { PlayerView, Scene } from '../types';

const WIDTH = 1920;
const HEIGHT = 1080;

let fontsRegistered = false;
function ensureFonts() {
  if (fontsRegistered) return;
  const candidates = ['C:\\Windows\\Fonts\\msyh.ttc', 'C:\\Windows\\Fonts\\simhei.ttf'];
  for (const p of candidates) {
    if (fs.existsSync(p)) {
      GlobalFonts.registerFromPath(p, 'WolfSans');
      break;
    }
  }
  fontsRegistered = true;
}

const ROLE_LABELS: Record<string, string> = {
  werewolf: '狼人',
  seer: '预言家',
  witch: '女巫',
  hunter: '猎人',
  villager: '平民',
};

const ROLE_COLORS: Record<string, string> = {
  werewolf: '#e0574c',
  seer: '#5b9bd5',
  witch: '#9b6fd1',
  hunter: '#d5a05b',
  villager: '#8a93a3',
};

const AVATAR_PALETTE = ['#e0574c', '#4caf82', '#5b9bd5', '#d5a05b', '#9b6fd1', '#4bb6c9', '#c9599b', '#7ac74c'];

function avatarColorFor(name: string): string {
  let hash = 0;
  for (let i = 0; i < name.length; i++) hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
  return AVATAR_PALETTE[hash % AVATAR_PALETTE.length];
}

export async function renderFrame(scene: Scene, players: PlayerView[], outPath: string): Promise<void> {
  ensureFonts();
  const canvas = createCanvas(WIDTH, HEIGHT);
  const ctx = canvas.getContext('2d');

  drawBackground(ctx);
  drawTitle(ctx, scene);
  drawSeats(ctx, players, scene);

  if (scene.kind === 'caption') {
    drawCaptionPill(ctx, scene.text);
  } else {
    drawSpeechCard(ctx, scene);
  }

  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, canvas.toBuffer('image/png'));
}

function drawBackground(ctx: SKRSContext2D) {
  const gradient = ctx.createLinearGradient(0, 0, 0, HEIGHT);
  gradient.addColorStop(0, '#1b2030');
  gradient.addColorStop(1, '#0c0e16');
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, WIDTH, HEIGHT);
}

function drawTitle(ctx: SKRSContext2D, scene: Scene) {
  const phaseLabel: Record<string, string> = {
    intro: '入座',
    sheriff_election: '警长竞选',
    night: '夜晚',
    day_announce: '公布死讯',
    day_discuss: '白天讨论',
    day_vote: '投票',
    day_pk_revote: 'PK重新投票',
    settling: '结算',
  };
  ctx.textAlign = 'center';
  ctx.fillStyle = '#e8eaed';
  ctx.font = '700 52px WolfSans';
  ctx.fillText('AI狼人杀 · 上帝视角回放', WIDTH / 2, 80);
  ctx.font = '400 30px WolfSans';
  ctx.fillStyle = '#9aa2b1';
  const dayText = scene.day > 0 ? `第${scene.day}天 · ${phaseLabel[scene.phase] || scene.phase}` : phaseLabel[scene.phase] || scene.phase;
  ctx.fillText(dayText, WIDTH / 2, 122);
}

function drawSeats(ctx: SKRSContext2D, players: PlayerView[], scene: Scene) {
  const sorted = [...players].sort((a, b) => a.seatNo - b.seatNo);
  const mid = Math.ceil(sorted.length / 2);
  const leftSeats = sorted.slice(0, mid);
  const rightSeats = sorted.slice(mid);

  layoutColumn(ctx, leftSeats, 40, scene, false);
  layoutColumn(ctx, rightSeats, WIDTH - 40, scene, true);
}

function layoutColumn(ctx: SKRSContext2D, seats: PlayerView[], edgeX: number, scene: Scene, alignRight: boolean) {
  const top = 180;
  const gap = 158;
  const avatarR = 46;

  seats.forEach((p, i) => {
    const cy = top + i * gap + avatarR;
    const cx = alignRight ? edgeX - avatarR : edgeX + avatarR;
    const isDead = p.eliminatedDay !== null && p.eliminatedDay <= scene.day && scene.phase !== 'intro' && scene.day > 0;
    const isSpeaking = scene.seatNo === p.seatNo;

    if (isSpeaking) {
      ctx.save();
      ctx.shadowColor = '#f0c419';
      ctx.shadowBlur = 24;
      ctx.strokeStyle = '#f0c419';
      ctx.lineWidth = 5;
      ctx.beginPath();
      ctx.arc(cx, cy, avatarR + 6, 0, Math.PI * 2);
      ctx.stroke();
      ctx.restore();
    }

    ctx.beginPath();
    ctx.arc(cx, cy, avatarR, 0, Math.PI * 2);
    ctx.fillStyle = avatarColorFor(p.displayName);
    ctx.globalAlpha = isDead ? 0.35 : 1;
    ctx.fill();
    ctx.fillStyle = '#ffffff';
    ctx.font = '700 40px WolfSans';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(p.displayName.slice(0, 1), cx, cy + 2);
    ctx.globalAlpha = 1;

    if (isDead) {
      ctx.strokeStyle = '#ff4d4d';
      ctx.lineWidth = 6;
      for (const offset of [-14, 0, 14]) {
        ctx.beginPath();
        ctx.moveTo(cx - avatarR + 6, cy - avatarR + offset + 20);
        ctx.lineTo(cx + avatarR - 6, cy + avatarR + offset - 20);
        ctx.stroke();
      }
    }

    const textX = alignRight ? edgeX - avatarR * 2 - 16 : edgeX + avatarR * 2 + 16;
    ctx.textAlign = alignRight ? 'right' : 'left';
    ctx.fillStyle = '#e8eaed';
    ctx.font = '700 28px WolfSans';
    ctx.fillText(`${p.seatNo}号 ${p.displayName}`, textX, cy - 10);

    const roleColor = p.inGameRole ? ROLE_COLORS[p.inGameRole] || '#8a93a3' : '#8a93a3';
    ctx.fillStyle = roleColor;
    ctx.font = '700 24px WolfSans';
    const roleText = (p.inGameRole ? ROLE_LABELS[p.inGameRole] || p.inGameRole : '') + (p.sheriff ? ' [警长]' : '');
    ctx.fillText(roleText, textX, cy + 22);
  });
}

function drawCaptionPill(ctx: SKRSContext2D, text: string) {
  const maxWidth = 1200;
  ctx.font = '700 36px WolfSans';
  const lines = wrapText(ctx, text, maxWidth - 80);
  const lineHeight = 50;
  const pillHeight = lines.length * lineHeight + 56;
  const pillY = HEIGHT / 2 - pillHeight / 2;

  ctx.fillStyle = 'rgba(10, 12, 20, 0.88)';
  roundRect(ctx, WIDTH / 2 - maxWidth / 2, pillY, maxWidth, pillHeight, 28);
  ctx.fill();

  ctx.fillStyle = '#ffffff';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  lines.forEach((line, i) => {
    ctx.fillText(line, WIDTH / 2, pillY + 28 + lineHeight * i + lineHeight / 2);
  });
}

function drawSpeechCard(ctx: SKRSContext2D, scene: Scene) {
  const cardWidth = 1300;
  const cardX = WIDTH / 2 - cardWidth / 2;
  const cardY = 210;
  const cardMaxHeight = 700;

  ctx.font = '400 34px WolfSans';
  let fontSize = 34;
  let lines = wrapText(ctx, scene.text, cardWidth - 100, fontSize);
  let lineHeight = fontSize * 1.5;
  while (lines.length * lineHeight + 140 > cardMaxHeight && fontSize > 20) {
    fontSize -= 2;
    ctx.font = `400 ${fontSize}px WolfSans`;
    lines = wrapText(ctx, scene.text, cardWidth - 100, fontSize);
    lineHeight = fontSize * 1.5;
  }
  // 字号已经缩到下限仍装不下：只截断"画面显示"的行数，配音仍然播完整段文字，
  // 屏幕字幕缩略、声音完整，是常见的口播视频处理方式，比硬挤爆卡片边界更体面
  const maxLines = Math.floor((cardMaxHeight - 140) / lineHeight);
  if (lines.length > maxLines) {
    const kept = lines.slice(0, Math.max(1, maxLines));
    kept[kept.length - 1] = kept[kept.length - 1].replace(/[…。，、]*$/, '') + '……';
    lines = kept;
  }
  const cardHeight = Math.min(cardMaxHeight, lines.length * lineHeight + 140);

  ctx.fillStyle = 'rgba(245, 241, 232, 0.97)';
  roundRect(ctx, cardX, cardY, cardWidth, cardHeight, 24);
  ctx.fill();

  ctx.fillStyle = '#333333';
  ctx.font = '700 32px WolfSans';
  ctx.textAlign = 'left';
  ctx.textBaseline = 'alphabetic';
  const header = `${scene.seatNo}号 ${scene.speakerName}`;
  ctx.fillText(header, cardX + 44, cardY + 56);

  if (scene.claimedRole) {
    ctx.fillStyle = '#c0392b';
    ctx.font = '700 24px WolfSans';
    ctx.textAlign = 'right';
    ctx.fillText(`自称：${ROLE_LABELS[scene.claimedRole] || scene.claimedRole}`, cardX + cardWidth - 44, cardY + 50);
  }

  ctx.strokeStyle = 'rgba(0,0,0,0.1)';
  ctx.beginPath();
  ctx.moveTo(cardX + 32, cardY + 76);
  ctx.lineTo(cardX + cardWidth - 32, cardY + 76);
  ctx.stroke();

  // 必须重新设回正文字号——上面画标题栏/自称标签时把ctx.font改成了32px/24px加粗，
  // 若不重设，实际绘制的字体会比wrapText换行时用的字号更宽，导致文字超出卡片右边界
  ctx.font = `400 ${fontSize}px WolfSans`;
  ctx.fillStyle = '#222222';
  ctx.textAlign = 'left';
  ctx.textBaseline = 'alphabetic';
  lines.forEach((line, i) => {
    ctx.fillText(line, cardX + 44, cardY + 120 + lineHeight * i);
  });
}

function roundRect(ctx: SKRSContext2D, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

function wrapText(ctx: SKRSContext2D, text: string, maxWidth: number, fontSize?: number): string[] {
  if (fontSize) ctx.font = `400 ${fontSize}px WolfSans`;
  const lines: string[] = [];
  const paragraphs = text.split(/\n+/);
  for (const para of paragraphs) {
    let current = '';
    for (const ch of para) {
      const test = current + ch;
      if (ctx.measureText(test).width > maxWidth && current.length > 0) {
        lines.push(current);
        current = ch;
      } else {
        current = test;
      }
    }
    lines.push(current);
  }
  return lines;
}
