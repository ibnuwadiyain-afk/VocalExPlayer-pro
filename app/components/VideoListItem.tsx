import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { THEME } from '../../constants';
import { VideoItem, VideoStatus } from '../../types';

interface VideoListItemProps {
  item: VideoItem;
  onPress: () => void;
  onLongPress: () => void;
}

export const VideoListItem: React.FC<VideoListItemProps> = ({
  item,
  onPress,
  onLongPress,
}) => {
  const formatDuration = (ms: number): string => {
    const totalSeconds = Math.max(0, Math.floor(ms / 1000));
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  };

  const getStatusBadge = (status: VideoStatus) => {
    switch (status) {
      case 'ready':
        return { label: 'Ready', bg: 'rgba(0, 212, 170, 0.15)', color: THEME.success };
      case 'processing':
        return { label: 'Processing', bg: 'rgba(255, 213, 79, 0.15)', color: THEME.warning };
      case 'error':
        return { label: 'Error', bg: 'rgba(255, 82, 82, 0.15)', color: THEME.error };
      default:
        return { label: 'New', bg: 'rgba(129, 212, 250, 0.15)', color: '#81D4FA' };
    }
  };

  const badge = getStatusBadge(item.status);

  return (
    <TouchableOpacity
      style={styles.card}
      onPress={onPress}
      onLongPress={onLongPress}
      activeOpacity={0.7}
    >
      <View style={styles.thumbnail}>
        <Text style={styles.thumbnailIcon}>▶</Text>
      </View>

      <View style={styles.infoContainer}>
        <Text style={styles.title} numberOfLines={1}>
          {item.title}
        </Text>
        <Text style={styles.duration}>{formatDuration(item.duration)}</Text>
      </View>

      <View style={[styles.badge, { backgroundColor: badge.bg }]}>
        <Text style={[styles.badgeText, { color: badge.color }]}>{badge.label}</Text>
      </View>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  card: {
    backgroundColor: THEME.surface,
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
    borderRadius: 14,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: THEME.border,
  },
  thumbnail: {
    width: 60,
    height: 60,
    borderRadius: 8,
    backgroundColor: '#262626',
    justifyContent: 'center',
    alignItems: 'center',
  },
  thumbnailIcon: {
    color: THEME.accentTeal,
    fontSize: 20,
  },
  infoContainer: {
    flex: 1,
    marginLeft: 14,
    justifyContent: 'center',
  },
  title: {
    color: THEME.textPrimary,
    fontSize: 15,
    fontWeight: '600',
    marginBottom: 4,
  },
  duration: {
    color: THEME.textSecondary,
    fontSize: 13,
  },
  badge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
  },
  badgeText: {
    fontSize: 12,
    fontWeight: '600',
  },
});
