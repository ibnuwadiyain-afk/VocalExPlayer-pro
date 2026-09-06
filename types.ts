export type VideoStatus = 'new' | 'processing' | 'ready' | 'error';

export interface VideoItem {
  id: string;
  videoUri: string;        // Original video path
  title: string;
  duration: number;        // in milliseconds
  status: VideoStatus;
  vocalUri?: string;       // Path to extracted vocal track WAV
  errorMessage?: string;
  progress?: number;       // 0 to 1
  createdAt?: number;
}

export interface SeparationProgressCallback {
  (progress: number, stage: string): void;
}

export interface ModelConfig {
  modelFileName: string;
  sampleRate: number;      // 44100
  chunkSize: number;       // 44100 or 66150
  hopSize: number;         // 22050 (50% overlap)
  channels: number;        // 2 (Stereo)
  batchSize: number;       // 1
}
