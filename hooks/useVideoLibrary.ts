import { useState, useEffect, useCallback } from 'react';
import { VideoItem } from '../types';
import { VideoStorageService } from '../services/storage';

export function useVideoLibrary() {
  const [videos, setVideos] = useState<VideoItem[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const loadLibrary = useCallback(async () => {
    setIsLoading(true);
    try {
      await VideoStorageService.initDirectories();
      const items = await VideoStorageService.loadVideos();
      setVideos(items);
    } catch (e) {
      console.error('Failed to load video library', e);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadLibrary();
  }, [loadLibrary]);

  const addVideo = useCallback(async (video: VideoItem) => {
    const updated = await VideoStorageService.addVideo(video);
    setVideos(updated);
    return updated;
  }, []);

  const updateVideo = useCallback(async (video: VideoItem) => {
    const updated = await VideoStorageService.updateVideo(video);
    setVideos(updated);
    return updated;
  }, []);

  const deleteVideo = useCallback(async (id: string) => {
    const updated = await VideoStorageService.deleteVideo(id);
    setVideos(updated);
    return updated;
  }, []);

  return {
    videos,
    isLoading,
    refresh: loadLibrary,
    addVideo,
    updateVideo,
    deleteVideo,
  };
}
