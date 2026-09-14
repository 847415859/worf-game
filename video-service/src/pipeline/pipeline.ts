import * as path from 'path';
import { fetchReplay } from './replayClient';
import { buildScript } from './script';
import { synthesize } from './tts';
import { renderFrame } from './canvasRender';
import { renderSegment, concatSegments, generateSilence } from './ffmpegAssemble';

export interface ProgressCallback {
  (done: number, total: number): void;
}

export async function generateVideo(sessionId: number, jobDir: string, onProgress: ProgressCallback): Promise<string> {
  const replay = await fetchReplay(sessionId);
  const scenes = buildScript(replay);
  if (scenes.length === 0) {
    throw new Error('该对局没有可用于生成视频的事件');
  }

  const segmentPaths: string[] = [];
  for (const scene of scenes) {
    const audioPath = path.join(jobDir, `audio_${scene.index}.mp3`);
    try {
      await synthesize(scene.text, scene.voice, audioPath);
    } catch (err) {
      // TTS多次重试仍失败时不要让整条视频流产，改用静音兜底顶上，
      // 与文档8.7/8.8节"生成失败也要保证流程能继续推进"是同一思路
      // eslint-disable-next-line no-console
      console.warn(`场景${scene.index}配音失败，改用静音占位: ${err instanceof Error ? err.message : err}`);
      const fallbackSeconds = Math.max(2, Math.min(10, scene.text.length / 4));
      await generateSilence(fallbackSeconds, audioPath);
    }
    const framePath = path.join(jobDir, `frame_${scene.index}.png`);
    await renderFrame(scene, replay.players, framePath);
    const segmentPath = path.join(jobDir, `segment_${scene.index}.mp4`);
    await renderSegment(framePath, audioPath, segmentPath);
    segmentPaths.push(segmentPath);
    onProgress(scene.index + 1, scenes.length);
  }

  const finalPath = path.join(jobDir, 'final.mp4');
  await concatSegments(segmentPaths, finalPath);
  return finalPath;
}
