import React, { useState, useEffect, useRef } from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { Video, ResizeMode, AVPlaybackStatus } from 'expo-av';
import { Audio } from 'expo-av';
import Slider from '@react-native-community/slider';
import { THEME } from '../../constants';
import { VideoItem } from '../../types';

interface VideoPlayerProps {
  video: VideoItem;
  onBack: () => void;
  onRetry?: () => void;
}

export const VideoPlayer: React.FC<VideoPlayerProps> = ({
  video,
  onBack,
  onRetry,
}) => {
  const videoRef = useRef<Video>(null);
  const vocalSoundRef = useRef<Audio.Sound | null>(null);

  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [positionMillis, setPositionMillis] = useState<number>(0);
  const [durationMillis, setDurationMillis] = useState<number>(video.duration || 1);
  const [useIsolatedVocals, setUseIsolatedVocals] = useState<boolean>(true);

  // Setup isolated vocal audio track if ready
  useEffect(() => {
    let isMounted = true;

    async function loadVocalTrack() {
      if (video.status === 'ready' && video.vocalUri) {
        try {
          if (vocalSoundRef.current) {
            await vocalSoundRef.current.unloadAsync();
          }
          const { sound } = await Audio.Sound.createAsync(
            { uri: video.vocalUri },
            { shouldPlay: false, volume: 1.0 }
          );
          if (isMounted) {
            vocalSoundRef.current = sound;
          }
        } catch (err) {
          console.warn('Could not load vocal audio track', err);
        }
      }
    }

    loadVocalTrack();

    return () => {
      isMounted = false;
      if (vocalSoundRef.current) {
        vocalSoundRef.current.unloadAsync();
      }
    };
  }, [video.status, video.vocalUri]);

  // Handle Play/Pause
  const togglePlayPause = async () => {
    if (!videoRef.current) return;

    if (isPlaying) {
      await videoRef.current.pauseAsync();
      if (vocalSoundRef.current && useIsolatedVocals) {
        await vocalSoundRef.current.pauseAsync();
      }
      setIsPlaying(false);
    } else {
      await videoRef.current.playAsync();
      if (vocalSoundRef.current && useIsolatedVocals) {
        await vocalSoundRef.current.playAsync();
      }
      setIsPlaying(true);
    }
  };

  // Video status update callback & sync check
  const onPlaybackStatusUpdate = async (status: AVPlaybackStatus) => {
    if (!status.isLoaded) return;

    setPositionMillis(status.positionMillis);
    if (status.durationMillis) {
      setDurationMillis(status.durationMillis);
    }
    setIsPlaying(status.isPlaying);

    // Synchronize vocal audio track with video presentation time
    if (useIsolatedVocals && vocalSoundRef.current && status.isPlaying) {
      const vocalStatus = await vocalSoundRef.current.getStatusAsync();
      if (vocalStatus.isLoaded) {
        const drift = Math.abs(status.positionMillis - vocalStatus.positionMillis);
        if (drift > 100) {
          await vocalSoundRef.current.setPositionAsync(status.positionMillis);
        }
      }
    }
  };

  const onSeek = async (value: number) => {
    const targetMs = value * durationMillis;
    if (videoRef.current) {
      await videoRef.current.setPositionAsync(targetMs);
    }
    if (vocalSoundRef.current && useIsolatedVocals) {
      await vocalSoundRef.current.setPositionAsync(targetMs);
    }
    setPositionMillis(targetMs);
  };

  const formatTime = (ms: number) => {
    const totalSec = Math.max(0, Math.floor(ms / 1000));
    const m = Math.floor(totalSec / 60);
    const s = totalSec % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={onBack} style={styles.backButton}>
          <Text style={styles.backText}>← Back</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle} numberOfLines={1}>
          {video.title}
        </Text>
      </View>

      {/* Video View */}
      <View style={styles.videoContainer}>
        {video.status === 'ready' ? (
          <Video
            ref={videoRef}
            source={{ uri: video.videoUri }}
            style={styles.video}
            resizeMode={ResizeMode.CONTAIN}
            isLooping={false}
            isMuted={useIsolatedVocals && !!video.vocalUri}
            onPlaybackStatusUpdate={onPlaybackStatusUpdate}
          />
        ) : (
          <View style={styles.placeholder}>
            <Text style={styles.placeholderText}>
              {video.status === 'error'
                ? 'Processing failed'
                : 'Extracting vocals with AI…'}
            </Text>
          </View>
        )}
      </View>

      {/* Controls */}
      <View style={styles.controlsContainer}>
        {video.status === 'error' ? (
          <View style={styles.errorBox}>
            <Text style={styles.errorTitle}>Vocal separation failed</Text>
            <Text style={styles.errorDesc}>{video.errorMessage || 'ONNX inference error'}</Text>
            {onRetry && (
              <TouchableOpacity style={styles.retryButton} onPress={onRetry}>
                <Text style={styles.retryText}>Retry Extraction</Text>
              </TouchableOpacity>
            )}
          </View>
        ) : (
          <>
            {/* Mode Switcher */}
            <View style={styles.modeRow}>
              <TouchableOpacity
                style={[styles.chip, useIsolatedVocals && styles.chipActive]}
                onPress={() => setUseIsolatedVocals(true)}
              >
                <Text style={[styles.chipText, useIsolatedVocals && styles.chipTextActive]}>
                  AI Isolated Vocals
                </Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={[styles.chip, !useIsolatedVocals && styles.chipActive]}
                onPress={() => setUseIsolatedVocals(false)}
              >
                <Text style={[styles.chipText, !useIsolatedVocals && styles.chipTextActive]}>
                  Original Audio
                </Text>
              </TouchableOpacity>
            </View>

            {/* Scrubber */}
            <Slider
              style={styles.slider}
              minimumValue={0}
              maximumValue={1}
              value={positionMillis / Math.max(1, durationMillis)}
              onSlidingComplete={onSeek}
              minimumTrackTintColor={THEME.accentTeal}
              maximumTrackTintColor="#333333"
              thumbTintColor={THEME.accentTeal}
            />

            {/* Timestamps */}
            <View style={styles.timeRow}>
              <Text style={styles.timeText}>{formatTime(positionMillis)}</Text>
              <Text style={styles.timeText}>{formatTime(durationMillis)}</Text>
            </View>

            {/* Play/Pause Button */}
            <View style={styles.playRow}>
              <TouchableOpacity style={styles.playButton} onPress={togglePlayPause}>
                <Text style={styles.playIcon}>{isPlaying ? '❚❚' : '▶'}</Text>
              </TouchableOpacity>
            </View>

            {/* Model Tag */}
            <View style={styles.infoBadge}>
              <Text style={styles.infoBadgeText}>
                Model: UVR MDX-Net ONNX | Synced AI Audio Track
              </Text>
            </View>
          </>
        )}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: THEME.background,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingTop: 50,
    paddingBottom: 16,
  },
  backButton: {
    marginRight: 12,
  },
  backText: {
    color: THEME.accentTeal,
    fontSize: 16,
    fontWeight: '600',
  },
  headerTitle: {
    color: THEME.textPrimary,
    fontSize: 16,
    fontWeight: '600',
    flex: 1,
  },
  videoContainer: {
    width: '100%',
    aspectRatio: 16 / 9,
    backgroundColor: '#000000',
    justifyContent: 'center',
    alignItems: 'center',
  },
  video: {
    width: '100%',
    height: '100%',
  },
  placeholder: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  placeholderText: {
    color: THEME.textSecondary,
    fontSize: 14,
  },
  controlsContainer: {
    padding: 20,
  },
  modeRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 20,
  },
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: THEME.surface,
    borderWidth: 1,
    borderColor: THEME.border,
  },
  chipActive: {
    backgroundColor: THEME.accentTeal,
    borderColor: THEME.accentTeal,
  },
  chipText: {
    color: THEME.textSecondary,
    fontSize: 13,
    fontWeight: '500',
  },
  chipTextActive: {
    color: '#000000',
    fontWeight: '700',
  },
  slider: {
    width: '100%',
    height: 40,
  },
  timeRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: -8,
    marginBottom: 20,
  },
  timeText: {
    color: THEME.textSecondary,
    fontSize: 12,
  },
  playRow: {
    alignItems: 'center',
    marginBottom: 24,
  },
  playButton: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: THEME.accentTeal,
    justifyContent: 'center',
    alignItems: 'center',
  },
  playIcon: {
    color: '#000000',
    fontSize: 24,
    fontWeight: 'bold',
  },
  infoBadge: {
    backgroundColor: THEME.surface,
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  infoBadgeText: {
    color: THEME.textSecondary,
    fontSize: 12,
  },
  errorBox: {
    backgroundColor: THEME.surface,
    padding: 20,
    borderRadius: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: THEME.border,
  },
  errorTitle: {
    color: THEME.error,
    fontSize: 16,
    fontWeight: '700',
    marginBottom: 6,
  },
  errorDesc: {
    color: THEME.textSecondary,
    fontSize: 13,
    marginBottom: 16,
    textAlign: 'center',
  },
  retryButton: {
    backgroundColor: THEME.accentTeal,
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 8,
  },
  retryText: {
    color: '#000000',
    fontWeight: 'bold',
  },
});
