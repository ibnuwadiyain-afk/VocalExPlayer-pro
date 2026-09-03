import AsyncStorage from '@react-native-async-storage/async-storage';
import * as FileSystem from 'expo-file-system';
import { STORAGE_KEYS } from '../constants';
import { VideoItem } from '../types';

export class VideoStorageService {
  private static VOCALS_DIR = `${FileSystem.documentDirectory}vocals/`;
  private static MODELS_DIR = `${FileSystem.documentDirectory}models/`;

  static async initDirectories(): Promise<void> {
    const vocalsDirInfo = await FileSystem.getInfoAsync(this.VOCALS_DIR);
    if (!vocalsDirInfo.exists) {
      await FileSystem.makeDirectoryAsync(this.VOCALS_DIR, { intermediates: true });
    }

    const modelsDirInfo = await FileSystem.getInfoAsync(this.MODELS_DIR);
    if (!modelsDirInfo.exists) {
      await FileSystem.makeDirectoryAsync(this.MODELS_DIR, { intermediates: true });
    }
  }

  static getVocalTrackPath(videoId: string): string {
    return `${this.VOCALS_DIR}vocal_${videoId}.wav`;
  }

  static getModelDirectory(): string {
    return this.MODELS_DIR;
  }

  static async loadVideos(): Promise<VideoItem[]> {
    try {
      const data = await AsyncStorage.getItem(STORAGE_KEYS.VIDEOS_LIBRARY);
      if (!data) return [];
      return JSON.parse(data) as VideoItem[];
    } catch (e) {
      console.error('Failed to load video library from storage', e);
      return [];
    }
  }

  static async saveVideos(videos: VideoItem[]): Promise<void> {
    try {
      await AsyncStorage.setItem(STORAGE_KEYS.VIDEOS_LIBRARY, JSON.stringify(videos));
    } catch (e) {
      console.error('Failed to persist video library', e);
      throw e;
    }
  }

  static async addVideo(video: VideoItem): Promise<VideoItem[]> {
    const existing = await this.loadVideos();
    const updated = [video, ...existing.filter(v => v.id !== video.id)];
    await this.saveVideos(updated);
    return updated;
  }

  static async updateVideo(updatedVideo: VideoItem): Promise<VideoItem[]> {
    const existing = await this.loadVideos();
    const updated = existing.map(v => (v.id === updatedVideo.id ? updatedVideo : v));
    await this.saveVideos(updated);
    return updated;
  }

  static async deleteVideo(id: string): Promise<VideoItem[]> {
    const existing = await this.loadVideos();
    const target = existing.find(v => v.id === id);

    if (target?.vocalUri) {
      try {
        const fileInfo = await FileSystem.getInfoAsync(target.vocalUri);
        if (fileInfo.exists) {
          await FileSystem.deleteAsync(target.vocalUri);
        }
      } catch (err) {
        console.warn('Failed to delete cached vocal audio file', err);
      }
    }

    const updated = existing.filter(v => v.id !== id);
    await this.saveVideos(updated);
    return updated;
  }
}
