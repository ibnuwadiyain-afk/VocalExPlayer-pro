# VocalExtractor — AI Video Vocal Isolation (UVR MDX-Net ONNX)

A high-performance Android & Expo application that plays video files with **real AI-extracted vocal tracks** powered by the **Ultimate Vocal Remover (UVR) MDX-Net** architecture using local **ONNX Runtime** neural network inference.

---

## Architecture Overview

1. **Audio Decoding**: The video's audio track is decoded into stereo 44.1kHz `Float32` linear PCM.
2. **Chunking & Windowing**: Audio is segmented into overlapping frames with a 50% hop-size and Hanning windowing to prevent boundary clicks.
3. **Neural Network Inference**: Chunks are processed through the UVR MDX-Net ONNX model (`ort.InferenceSession.run`) yielding isolated vocal spectrograms / waveforms.
4. **Overlap-Add (OLA) Synthesis**: The separated segments are accumulated and normalized into a continuous 44.1kHz stereo vocal WAV file.
5. **Synchronized Playback**: The video player renders the video while muting the original audio and playing the AI-extracted vocal track in lockstep, with drift correction and instant mode toggling between AI Vocals and Original Audio.

---

## Expected Model Tensor Specifications

| Parameter | Value |
|---|---|
| **Input Name** | `input` |
| **Input Tensor Shape** | `[1, 2, 44100]` *(Batch: 1, Stereo: 2, Samples: 44100)* |
| **Input Data Type** | `Float32` (normalized between `-1.0` and `1.0`) |
| **Sample Rate** | `44,100 Hz` |
| **Output Name** | `output` |
| **Output Tensor Shape** | `[1, 2, 44100]` *(Batch: 1, Stereo: 2, Samples: 44100)* |
| **Output Data Type** | `Float32` (isolated vocals) |

---

## Where to Download UVR ONNX Models

Pre-trained UVR / MDX-Net checkpoints are publicly available from the Ultimate Vocal Remover community:

1. **UVR Model Repository**:
   - Official Hugging Face: [https://huggingface.co/Anjok07/ultimatevocalremover_models](https://huggingface.co/Anjok07/ultimatevocalremover_models)
   - Popular Recommended Checkpoints:
     - `UVR_MDXNET_1_9703.onnx` (Best balance of quality and speed on mobile)
     - `UVR-MDX-NET-Voc_FT.onnx` (Fine-tuned for vocal extraction clarity)
     - `UVR_MDXNET_KARA.onnx` (Ideal for karaoke / vocal removal)

2. **Placing the Model in Assets**:
   - Copy the `.onnx` file to:
     - Android native: `app/src/main/assets/models/UVR_MDXNET_1_9703.onnx`
     - Expo: `assets/models/UVR_MDXNET_1_9703.onnx`

---

## PyTorch to ONNX Conversion

If you have a `.ckpt` or `.pth` model file from PyTorch:

```bash
python scripts/convert_mdx_to_onnx.py \
  --checkpoint path/to/UVR_MDXNET_1_9703.ckpt \
  --output app/src/main/assets/models/UVR_MDXNET_1_9703.onnx \
  --chunk_size 44100
```

---

## Setup & Running the Application

### Option A: Streaming Android Emulator / Native Android Build
The native Android app has been configured with ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime-android`), Media3 ExoPlayer, and Room:

```bash
# Build the Android APK
gradle :app:assembleDebug
```

### Option B: Expo (React Native) Project

1. **Install Dependencies**:
```bash
npm install
```

2. **Run on Android**:
```bash
npx expo run:android
```

---

## Features & Usage

- **Gallery Import**: Tap "+ Import Video" to pick any video from your device storage.
- **Instant Demo**: Tap "Load Demo Music Video" to test the UVR MDX-Net pipeline immediately without importing files.
- **Offline AI Separation**: Processes the audio track through the ONNX model before playback with a progress overlay.
- **Cache Persistence**: Separated vocal tracks are saved locally in the app's cache directory so future playback is instant.
- **Audio Switcher**: Toggle between **AI Vocals Only** and **Original Audio** directly in the video player.
- **Translated SRT Dubbing & Export/Import**:
  - In the Dubbing dialog, customize target TTS language, voice speed, pitch, and translation service.
  - **Export Translated SRT**: Share or save translated subtitle cues as a `.srt` file.
  - **Import Translated SRT**: Import any pre-translated `.srt` file directly to preview cues and dub the video instantly—even if the video doesn't have an original transcript.
- **Management**: Long-press any video card in the list to delete it and remove its cached audio track.

---

## Direct GitHub Release APK Publishing

### 1. Automated via GitHub Actions
The repository includes `.github/workflows/android-build.yml` which builds the debug APK and publishes it directly to **GitHub Releases**:
- Runs automatically when pushing git tags (e.g. `v1.0.0`) or on push to `main`/`master`.
- Can be triggered manually via GitHub Actions **Run workflow** (`workflow_dispatch`) with optional custom tag and release title.
- Artifacts uploaded:
  - `VocalExtractor-debug.apk`
  - `VocalExtractor-v1.0.<run>-<sha>-debug.apk`
  - `app-debug.apk`

### 2. Push APK Directly via Terminal / Script
To push the built APK directly to a GitHub Release using the GitHub REST API:

```bash
# Set your GitHub token and repository
export GITHUB_TOKEN="ghp_yourPersonalAccessToken"
export GITHUB_REPOSITORY="owner/repo"

# Build APK (if not already built)
gradle assembleDebug

# Push to release directly
python3 scripts/push_to_github_release.py \
  --tag v1.0.0 \
  --title "VocalExtractor v1.0.0 Release" \
  --body "Android debug APK with offline vocal isolation, Whisper subtitles, and SRT dubbing."
```

Or using the bash wrapper:
```bash
./scripts/push_to_github_release.sh --tag v1.0.0
```

