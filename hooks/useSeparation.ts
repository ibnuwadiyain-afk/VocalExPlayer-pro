import { useState, useCallback } from 'react';
import { VideoItem } from '../types';
import { VocalSeparationEngine } from '../services/separation';
import { VideoStorageService } from '../services/storage';

export function useSeparation() {
  const [isProcessing, setIsProcessing] = useState<boolean>(false);
  const [progress, setProgress] = useState<number>(0);
  const [stageText, setStageText] = useState<string>('Extracting vocals with AI…');
  const [error, setError] = useState<string | null>(null);

  const startSeparation = useCallback(async (
    video: VideoItem,
    onSuccess?: (updated: VideoItem) => void,
    onError?: (errMessage: string) => void
  ) => {
    setIsProcessing(true);
    setProgress(0.05);
    setStageText('Extracting vocals with AI…');
    setError(null);

    // Set item status to processing
    const processingItem: VideoItem = {
      ...video,
      status: 'processing',
      progress: 0.05,
    };
    await VideoStorageService.updateVideo(processingItem);

    try {
      const vocalUri = await VocalSeparationEngine.extractVocals(
        video.id,
        video.videoUri,
        undefined,
        (p, stage) => {
          setProgress(p);
          setStageText(stage);
        }
      );

      const readyItem: VideoItem = {
        ...video,
        status: 'ready',
        vocalUri,
        progress: 1.0,
        errorMessage: undefined,
      };

      await VideoStorageService.updateVideo(readyItem);
      setIsProcessing(false);
      onSuccess?.(readyItem);
      return readyItem;
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Unknown separation error';
      setError(message);
      setIsProcessing(false);

      const errorItem: VideoItem = {
        ...video,
        status: 'error',
        errorMessage: message,
      };

      await VideoStorageService.updateVideo(errorItem);
      onError?.(message);
      return errorItem;
    }
  }, []);

  return {
    isProcessing,
    progress,
    stageText,
    error,
    startSeparation,
  };
}
