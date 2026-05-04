#!/usr/bin/env python3
"""
Whisper Speech-to-Text inference using ExecutorTorch Runtime via optimum-executorch.

This script demonstrates how to run Whisper inference using the optimum-executorch
library for automatic speech recognition (ASR).

Example usage:
    python whisper_inference.py --audio_path audio.wav
    python whisper_inference.py --audio_path audio.wav --model_dir ./models/whisper-tiny-ExecuTorch-XNNPACK

Requirements:
    - optimum-executorch
    - transformers
    - soundfile or librosa for audio loading
"""

import argparse
from pathlib import Path

import torch
from optimum.executorch import ExecuTorchModelForSpeechSeq2Seq
from transformers import WhisperProcessor


def load_audio(audio_path: str, sampling_rate: int = 16000) -> torch.Tensor:
    """Load audio file and resample to target sampling rate.

    Args:
        audio_path: Path to the audio file (WAV, MP3, etc.)
        sampling_rate: Target sampling rate (default: 16000 for Whisper)

    Returns:
        Audio waveform as a 1D tensor
    """
    try:
        import librosa
        audio, sr = librosa.load(audio_path, sr=sampling_rate, mono=True)
        return torch.from_numpy(audio).float()
    except ImportError:
        pass

    try:
        import soundfile as sf
        audio, sr = sf.read(audio_path)
        if len(audio.shape) > 1:
            audio = audio.mean(axis=1)  # Convert to mono
        if sr != sampling_rate:
            # Simple resampling using torch
            import torchaudio
            audio = torch.from_numpy(audio).float().unsqueeze(0)
            audio = torchaudio.functional.resample(audio, sr, sampling_rate)
            audio = audio.squeeze(0)
        else:
            audio = torch.from_numpy(audio).float()
        return audio
    except ImportError:
        pass

    raise ImportError(
        "Please install either librosa or soundfile for audio loading:\n"
        "  pip install librosa\n"
        "  or\n"
        "  pip install soundfile"
    )


def main():
    parser = argparse.ArgumentParser(
        description="Run Whisper speech-to-text inference with ExecutorTorch",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
    # Transcribe an audio file
    python whisper_inference.py --audio_path audio.wav

    # Use a specific model
    python whisper_inference.py --audio_path audio.wav --model_dir ./models/whisper-small-ExecuTorch-XNNPACK
        """,
    )
    parser.add_argument(
        "--model_dir",
        type=str,
        default="models/whisper-tiny-ExecuTorch-XNNPACK",
        help="Path to the model directory containing model.pte and tokenizer files",
    )
    parser.add_argument(
        "--audio_path",
        type=str,
        required=True,
        help="Path to the input audio file (WAV, MP3, etc.)",
    )
    parser.add_argument(
        "--max_seq_len",
        type=int,
        default=448,
        help="Maximum number of tokens to generate",
    )

    args = parser.parse_args()

    # Resolve paths relative to script directory
    script_dir = Path(__file__).parent
    model_dir = script_dir / args.model_dir
    audio_path = Path(args.audio_path)
    if not audio_path.is_absolute():
        audio_path = script_dir / audio_path

    print("=" * 60)
    print("Whisper Speech-to-Text Inference (optimum-executorch)")
    print("=" * 60)

    # Load audio
    print(f"Loading audio from {audio_path}...")
    audio = load_audio(str(audio_path))
    print(f"  Audio length: {len(audio) / 16000:.2f} seconds ({len(audio)} samples)")

    # Load processor (handles audio preprocessing)
    print(f"Loading processor from {model_dir}...")
    processor = WhisperProcessor.from_pretrained(str(model_dir))

    # Preprocess audio to get input features (log-mel spectrogram)
    print("Preprocessing audio...")
    input_features = processor(
        audio.numpy(),
        sampling_rate=16000,
        return_tensors="pt",
    ).input_features
    print(f"  Input features shape: {input_features.shape}")

    # Load the ExecuTorch model
    print(f"Loading model from {model_dir}...")
    model = ExecuTorchModelForSpeechSeq2Seq.from_pretrained(str(model_dir))

    print()
    print("-" * 50)
    print("Transcribing...")
    print("-" * 50)

    # Transcribe
    transcription = model.transcribe(
        tokenizer=processor.tokenizer,
        input_features=input_features,
        max_seq_len=args.max_seq_len,
    )

    print()
    print("=" * 60)
    print("Transcription:")
    print("=" * 60)
    print(transcription)
    print()
    print("Done!")


if __name__ == "__main__":
    main()
