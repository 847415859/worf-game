import express from 'express';
import * as path from 'path';
import * as fs from 'fs';
import { generateVideo } from './pipeline/pipeline';
import { createJob, getJob } from './jobStore';

const app = express();
const PORT = process.env.VIDEO_SERVICE_PORT ? Number(process.env.VIDEO_SERVICE_PORT) : 3200;
const OUTPUT_ROOT = path.join(__dirname, '..', 'output');

app.use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Headers', '*');
  res.header('Access-Control-Allow-Methods', '*');
  next();
});

/** 对已结算对局触发一次视频生成任务，见需求文档15节观赏性设计的落地延伸 */
app.post('/videos/:sessionId/generate', (req, res) => {
  const sessionId = Number(req.params.sessionId);
  if (!Number.isFinite(sessionId)) {
    return res.status(400).json({ error: 'sessionId不合法' });
  }
  const existing = getJob(String(sessionId));
  if (existing && existing.status === 'running') {
    return res.json({ jobId: existing.jobId, status: existing.status });
  }

  const job = createJob(sessionId);
  const jobDir = path.join(OUTPUT_ROOT, job.jobId);
  fs.mkdirSync(jobDir, { recursive: true });

  generateVideo(sessionId, jobDir, (done, total) => {
    job.done = done;
    job.total = total;
  })
    .then((outputPath) => {
      job.status = 'done';
      job.outputPath = outputPath;
    })
    .catch((err) => {
      job.status = 'error';
      job.error = err instanceof Error ? err.message : String(err);
      // eslint-disable-next-line no-console
      console.error(`视频生成失败 session=${sessionId}`, err);
    });

  res.json({ jobId: job.jobId, status: job.status });
});

app.get('/videos/:jobId/status', (req, res) => {
  const job = getJob(req.params.jobId);
  if (!job) return res.status(404).json({ error: '任务不存在' });
  res.json({
    jobId: job.jobId,
    status: job.status,
    done: job.done,
    total: job.total,
    error: job.error,
    downloadUrl: job.status === 'done' ? `/videos/${job.jobId}/download` : null,
  });
});

app.get('/videos/:jobId/download', (req, res) => {
  const job = getJob(req.params.jobId);
  if (!job || job.status !== 'done' || !job.outputPath) {
    return res.status(404).json({ error: '视频尚未生成完成' });
  }
  res.download(job.outputPath, `wolf-session-${job.sessionId}.mp4`);
});

app.listen(PORT, () => {
  // eslint-disable-next-line no-console
  console.log(`wolf-video-service listening on :${PORT}`);
});
