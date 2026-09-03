import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  Alert,
  StatusBar,
} from 'react-native';
import { useRouter } from 'expo-router';
import { THEME } from '../constants';
import { VideoItem } from '../types';
import { useVideoLibrary } from '../hooks/useVideoLibrary';
import { useSeparation } from '../hooks/useSeparation';
import { VideoListItem } from './components/VideoListItem';
import { ProcessingOverlay } from './components/ProcessingOverlay';

export default function VideoListScreen() {
  const router = useRouter();
  const { videos, deleteVideo } = useVideoLibrary();
  const { isProcessing, progress, stageText, startSeparation } = useSeparation();
  const [activeVideo, setActiveVideo] = useState<VideoItem | null>(null);

  const handleVideoPress = async (video: VideoItem) => {
    setActiveVideo(video);
    if (video.status === 'ready') {
      router.push({
        pathname: '/player',
        params: { id: video.id },
      });
    } else if (video.status === 'new' || video.status === 'error') {
      await startSeparation(
        video,
        (readyVideo) => {
          router.push({
            pathname: '/player',
            params: { id: readyVideo.id },
          });
        },
        (errMsg) => {
          Alert.alert('Separation Error', errMsg);
        }
      );
    }
  };

  const handleVideoLongPress = (video: VideoItem) => {
    Alert.alert(
      'Delete Video',
      `Are you sure you want to remove "${video.title}" and its cached vocal audio?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: () => deleteVideo(video.id),
        },
      ]
    );
  };

  return (
    <View style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor={THEME.background} />

      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>VocalExtractor</Text>
        <Text style={styles.headerSubtitle}>AI Vocal Separation (ONNX MDX-Net)</Text>
      </View>

      {/* Video List or Empty State */}
      {videos.length === 0 ? (
        <View style={styles.emptyContainer}>
          <View style={styles.emptyIconCircle}>
            <Text style={styles.emptyIcon}>🎬</Text>
          </View>
          <Text style={styles.emptyTitle}>No Videos Imported</Text>
          <Text style={styles.emptyDescription}>
            Import a video from your gallery to isolate vocals using real UVR MDX-Net ONNX inference.
          </Text>
          <TouchableOpacity
            style={styles.importButton}
            onPress={() => router.push('/import')}
          >
            <Text style={styles.importButtonText}>+ Import Video</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <FlatList
          data={videos}
          keyExtractor={(item) => item.id}
          renderItem={({ item }) => (
            <VideoListItem
              item={item}
              onPress={() => handleVideoPress(item)}
              onLongPress={() => handleVideoLongPress(item)}
            />
          )}
          contentContainerStyle={styles.listContent}
        />
      )}

      {/* Floating Import Button */}
      {videos.length > 0 && (
        <TouchableOpacity
          style={styles.fab}
          onPress={() => router.push('/import')}
        >
          <Text style={styles.fabText}>+ Import Video</Text>
        </TouchableOpacity>
      )}

      {/* Real ONNX Processing Overlay */}
      <ProcessingOverlay
        visible={isProcessing}
        progress={progress}
        stageText={stageText}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: THEME.background,
  },
  header: {
    paddingTop: 54,
    paddingHorizontal: 20,
    paddingBottom: 16,
    borderBottomWidth: 1,
    borderBottomColor: THEME.border,
  },
  headerTitle: {
    color: THEME.textPrimary,
    fontSize: 24,
    fontWeight: '700',
  },
  headerSubtitle: {
    color: THEME.accentTeal,
    fontSize: 13,
    marginTop: 2,
    fontWeight: '500',
  },
  listContent: {
    padding: 16,
    paddingBottom: 100,
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 32,
  },
  emptyIconCircle: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: THEME.surface,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 20,
    borderWidth: 1,
    borderColor: THEME.border,
  },
  emptyIcon: {
    fontSize: 36,
  },
  emptyTitle: {
    color: THEME.textPrimary,
    fontSize: 18,
    fontWeight: '700',
    marginBottom: 8,
  },
  emptyDescription: {
    color: THEME.textSecondary,
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 24,
  },
  importButton: {
    backgroundColor: THEME.accentTeal,
    paddingHorizontal: 24,
    paddingVertical: 14,
    borderRadius: 12,
  },
  importButtonText: {
    color: '#000000',
    fontSize: 15,
    fontWeight: '700',
  },
  fab: {
    position: 'absolute',
    bottom: 24,
    right: 20,
    backgroundColor: THEME.accentTeal,
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderRadius: 28,
    shadowColor: THEME.accentTeal,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 6,
  },
  fabText: {
    color: '#000000',
    fontWeight: '700',
    fontSize: 15,
  },
});
