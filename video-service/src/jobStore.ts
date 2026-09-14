export type JobStatus = 'running' | 'done' | 'error';

export interface Job {
  jobId: string;
  sessionId: number;
  status: JobStatus;
  done: number;
  total: number;
  error?: string;
  outputPath?: string;
}

const jobs = new Map<string, Job>();

export function createJob(sessionId: number): Job {
  const jobId = String(sessionId);
  const job: Job = { jobId, sessionId, status: 'running', done: 0, total: 0 };
  jobs.set(jobId, job);
  return job;
}

export function getJob(jobId: string): Job | undefined {
  return jobs.get(jobId);
}
