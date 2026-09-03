export const THEME = {
  background: '#121212',
  surface: '#1E1E1E',
  surfaceVariant: '#262626',
  accentTeal: '#00D4AA',
  accentTealDark: '#00382B',
  accentTealContainer: '#004F3F',
  textPrimary: '#F1F1F1',
  textSecondary: '#A0A0A0',
  border: '#333333',
  error: '#FF5252',
  warning: '#FFD54F',
  success: '#00D4AA',
};

export const MODEL_CONFIG = {
  defaultModelName: 'UVR_MDXNET_1_9703.onnx',
  fallbackModelName: 'UVR-MDX-NET-Voc_FT.onnx',
  sampleRate: 44100,
  chunkSize: 44100,        // 1.0 second per chunk
  hopSize: 22050,          // 50% overlap for Hanning window
  channels: 2,             // Stereo (Left, Right)
  batchSize: 1,
};

export const STORAGE_KEYS = {
  VIDEOS_LIBRARY: '@uvr_video_library',
  ACTIVE_MODEL: '@uvr_active_model_path',
};
