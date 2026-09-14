import { execFile } from 'child_process';
import { promisify } from 'util';
import * as fs from 'fs';
import * as path from 'path';

const execFileAsync = promisify(execFile);

export async function generateSilence(durationSeconds: number, outPath: string): Promise<void> {
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  await execFileAsync('ffmpeg', [
    '-y',
    '-f', 'lavfi',
    '-i', 'anullsrc=r=24000:cl=mono',
    '-t', String(durationSeconds),
    '-q:a', '9',
    outPath,
  ]);
}

export async function renderSegment(framePath: string, audioPath: string, outPath: string): Promise<void> {
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  await execFileAsync('ffmpeg', [
    '-y',
    '-loop', '1',
    '-i', framePath,
    '-i', audioPath,
    '-c:v', 'libx264',
    '-tune', 'stillimage',
    '-c:a', 'aac',
    '-b:a', '192k',
    '-pix_fmt', 'yuv420p',
    '-vf', 'fps=25,format=yuv420p',
    '-shortest',
    outPath,
  ]);
}

export async function concatSegments(segmentPaths: string[], outPath: string): Promise<void> {
  const listDir = path.dirname(outPath);
  fs.mkdirSync(listDir, { recursive: true });
  const listFile = path.join(listDir, 'concat_list.txt');
  const listContent = segmentPaths.map((p) => `file '${p.replace(/\\/g, '/')}'`).join('\n');
  fs.writeFileSync(listFile, listContent, 'utf-8');

  await execFileAsync('ffmpeg', [
    '-y',
    '-f', 'concat',
    '-safe', '0',
    '-i', listFile,
    '-c', 'copy',
    outPath,
  ]);
}
