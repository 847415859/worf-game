import { EdgeTTS } from 'node-edge-tts';
import { execFile } from 'child_process';
import { promisify } from 'util';
import { VoiceProfile } from '../types';

const execFileAsync = promisify(execFile);

const MAX_RETRY = 3;

export async function synthesize(text: string, voice: VoiceProfile, outPath: string): Promise<number> {
  let lastError: unknown;
  for (let attempt = 0; attempt <= MAX_RETRY; attempt++) {
    try {
      const tts = new EdgeTTS({
        voice: voice.voice,
        lang: 'zh-CN',
        pitch: voice.pitch,
        rate: voice.rate,
        timeout: 20000,
      });
      await tts.ttsPromise(text, outPath);
      return await probeDuration(outPath);
    } catch (err) {
      lastError = err;
      // 免费的Edge TTS服务偶尔会连接超时/断开，重试即可恢复，不是文本本身的问题
      await sleep(800 * (attempt + 1));
    }
  }
  throw new Error(`TTS合成失败，已重试${MAX_RETRY}次: ${lastError instanceof Error ? lastError.message : lastError}`);
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function probeDuration(filePath: string): Promise<number> {
  const { stdout } = await execFileAsync('ffprobe', [
    '-v', 'error',
    '-show_entries', 'format=duration',
    '-of', 'default=noprint_wrappers=1:nokey=1',
    filePath,
  ]);
  const seconds = parseFloat(stdout.trim());
  return Number.isFinite(seconds) ? seconds : 2;
}
