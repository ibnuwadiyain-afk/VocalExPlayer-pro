import React, { useMemo } from 'react';
import { View, StyleSheet, Text } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { THEME } from '../constants';
import { useVideoLibrary } from '../hooks/useVideoLibrary';
import { useSeparation } from '../hooks/useSeparation';
import { VideoPlayer } from './components/VideoPlayer';
import { ProcessingOverlay } from './components/ProcessingOverlay';

export default function PlayerScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const { videos } = useVideoLibrary();
  const { isProcessing, progress, stageText, startSeparation } = useSeparation();

  const currentVideo = useMemo(() => {
    return videos.find((v) => v.id === id);
  }, [videos, id]);

  if (!currentVideo) {
    return (
      <View style={styles.notFoundContainer}>
        <Text style={styles.notFoundText}>Video not found</Text>
      </View>
    );
  }

  const handleRetry = () => {
    startSeparation(currentVideo);
  };

  return (
    <View style={styles.container}>
      <VideoPlayer
        video={currentVideo}
        onBack={() => router.back()}
        onRetry={handleRetry}
      />

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
  notFoundContainer: {
    flex: 1,
    backgroundColor: THEME.background,
    justifyContent: 'center',
    alignItems: 'center',
  },
  notFoundText: {
    color: THEME.textSecondary,
    fontSize: 16,
  },
});
