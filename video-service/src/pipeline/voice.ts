import { VoiceProfile } from '../types';

/**
 * 免费的Edge TTS只有6个标准普通话音色，一局9-12人会不够用，
 * 用"音色+音高微调"的组合把6个拉伸成最多18种可感知区分的声音，
 * 见需求文档8.5节"拟人化技巧"——让每个座位听起来是不同的人。
 */
const BASE_VOICES = [
  'zh-CN-XiaoxiaoNeural',
  'zh-CN-XiaoyiNeural',
  'zh-CN-YunxiNeural',
  'zh-CN-YunxiaNeural',
  'zh-CN-YunjianNeural',
  'zh-CN-YunyangNeural',
];
const PITCH_VARIANTS = ['-10%', '+0%', '+10%'];

export const NARRATOR_VOICE: VoiceProfile = {
  voice: 'zh-CN-YunyangNeural',
  pitch: '-5%',
  rate: '+5%',
};

export function voiceForSeat(seatNo: number): VoiceProfile {
  const voiceIndex = seatNo % BASE_VOICES.length;
  const pitchIndex = Math.floor(seatNo / BASE_VOICES.length) % PITCH_VARIANTS.length;
  return {
    voice: BASE_VOICES[voiceIndex],
    pitch: PITCH_VARIANTS[pitchIndex],
    rate: '+0%',
  };
}
