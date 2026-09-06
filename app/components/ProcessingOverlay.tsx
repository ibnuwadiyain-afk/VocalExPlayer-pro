import React from 'react';
import { View, Text, StyleSheet, Modal } from 'react-native';
import { ActivityIndicator, ProgressBar } from 'react-native-paper';
import { THEME } from '../../constants';

interface ProcessingOverlayProps {
  visible: boolean;
  progress: number;
  stageText?: string;
}

export const ProcessingOverlay: React.FC<ProcessingOverlayProps> = ({
  visible,
  progress,
  stageText = 'Extracting vocals with AI…',
}) => {
  if (!visible) return null;

  return (
    <Modal transparent animationType="fade" visible={visible}>
      <View style={styles.overlay}>
        <View style={styles.card}>
          <ActivityIndicator size="large" color={THEME.accentTeal} />
          <Text style={styles.title}>{stageText}</Text>
          <ProgressBar
            progress={Math.max(0, Math.min(1, progress))}
            color={THEME.accentTeal}
            style={styles.progressBar}
          />
          <Text style={styles.modelTag}>UVR MDX-Net ONNX Inference Engine</Text>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.85)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  card: {
    width: '100%',
    maxWidth: 320,
    backgroundColor: THEME.surface,
    borderRadius: 20,
    padding: 24,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.4,
    shadowRadius: 16,
    elevation: 10,
    borderWidth: 1,
    borderColor: THEME.border,
  },
  title: {
    color: THEME.textPrimary,
    fontSize: 16,
    fontWeight: '600',
    marginTop: 18,
    marginBottom: 14,
    textAlign: 'center',
  },
  progressBar: {
    width: '100%',
    height: 6,
    borderRadius: 3,
    backgroundColor: '#333333',
    marginVertical: 10,
  },
  modelTag: {
    color: THEME.textSecondary,
    fontSize: 12,
    marginTop: 10,
  },
});
