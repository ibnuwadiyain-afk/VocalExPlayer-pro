import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { useRouter } from 'expo-router';
import * as ImagePicker from 'expo-image-picker';
import { THEME } from '../constants';
import { VideoItem } from '../types';
import { useVideoLibrary } from '../hooks/useVideoLibrary';

export default function ImportScreen() {
  const router = useRouter();
  const { addVideo } = useVideoLibrary();
  const [isImporting, setIsImporting] = useState<boolean>(false);

  const handlePickVideo = async () => {
    try {
      const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (!permission.granted) {
        Alert.alert(
          'Permission Required',
          'Media library permission is required to import videos.'
        );
        return;
      }

      setIsImporting(true);

      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ['videos'],
        allowsEditing: false,
        quality: 1,
      });

      if (!result.canceled && result.assets && result.assets.length > 0) {
        const asset = result.assets[0];
        const newId = 'vid_' + Date.now().toString(36);
        const fileName =
          asset.fileName || asset.uri.split('/').pop() || 'Imported Video';

        const newVideo: VideoItem = {
          id: newId,
          videoUri: asset.uri,
          title: fileName,
          duration: asset.duration ? Math.round(asset.duration) : 15000,
          status: 'new',
        };

        await addVideo(newVideo);
        router.replace('/');
      }
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Failed to pick video';
      Alert.alert('Import Error', msg);
    } finally {
      setIsImporting(false);
    }
  };

  return (
    <View style={styles.container}>
      {/* Top Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Text style={styles.backText}>Cancel</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Import Video</Text>
        <View style={{ width: 50 }} />
      </View>

      {/* Main Content */}
      <View style={styles.content}>
        <View style={styles.card}>
          <Text style={styles.cardIcon}>🎥</Text>
          <Text style={styles.cardTitle}>Choose Video from Gallery</Text>
          <Text style={styles.cardDesc}>
            Select any music video, concert recording, or vocal performance.
            The AI UVR MDX-Net model will extract and isolate the clean vocal track.
          </Text>

          {isImporting ? (
            <View style={styles.loadingContainer}>
              <ActivityIndicator size="large" color={THEME.accentTeal} />
              <Text style={styles.loadingText}>Reading video metadata…</Text>
            </View>
          ) : (
            <TouchableOpacity style={styles.actionButton} onPress={handlePickVideo}>
              <Text style={styles.actionButtonText}>Select from Device</Text>
            </TouchableOpacity>
          )}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: THEME.background,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: 54,
    paddingHorizontal: 20,
    paddingBottom: 16,
    borderBottomWidth: 1,
    borderBottomColor: THEME.border,
  },
  backButton: {
    paddingVertical: 4,
  },
  backText: {
    color: THEME.accentTeal,
    fontSize: 16,
  },
  headerTitle: {
    color: THEME.textPrimary,
    fontSize: 18,
    fontWeight: '700',
  },
  content: {
    flex: 1,
    padding: 24,
    justifyContent: 'center',
  },
  card: {
    backgroundColor: THEME.surface,
    padding: 28,
    borderRadius: 20,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: THEME.border,
  },
  cardIcon: {
    fontSize: 48,
    marginBottom: 16,
  },
  cardTitle: {
    color: THEME.textPrimary,
    fontSize: 20,
    fontWeight: '700',
    marginBottom: 12,
    textAlign: 'center',
  },
  cardDesc: {
    color: THEME.textSecondary,
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 22,
    marginBottom: 28,
  },
  actionButton: {
    backgroundColor: THEME.accentTeal,
    paddingHorizontal: 32,
    paddingVertical: 14,
    borderRadius: 12,
    width: '100%',
    alignItems: 'center',
  },
  actionButtonText: {
    color: '#000000',
    fontSize: 16,
    fontWeight: '700',
  },
  loadingContainer: {
    alignItems: 'center',
    paddingVertical: 10,
  },
  loadingText: {
    color: THEME.textSecondary,
    fontSize: 14,
    marginTop: 12,
  },
});
