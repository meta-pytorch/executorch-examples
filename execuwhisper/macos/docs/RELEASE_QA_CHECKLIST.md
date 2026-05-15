# ExecuWhisper Release QA Checklist

Use this checklist before publishing a release DMG.

## Build And Package

- Run `xcodebuild test -scheme ExecuWhisper -destination 'platform=macOS'`.
- Run `./scripts/build.sh`.
- Run `./scripts/create_dmg.sh ./build/Build/Products/Release/ExecuWhisper.app ./ExecuWhisper.dmg`.
- Run `./scripts/verify_release.sh ./ExecuWhisper.dmg`.

## First Launch

- Start from a clean machine or remove `~/Library/Application Support/ExecuWhisper/models`.
- Launch from the DMG-installed app.
- Verify model downloads complete.
- Verify Parakeet preload spinner appears and transitions to ready.
- Grant Microphone permission to ExecuWhisper.
- Grant Accessibility permission to ExecuWhisper Paste Helper.

## Audio Devices

Verify both manual Record and overlay dictation for each available class:

- Built-in Mac microphone.
- Bluetooth headset in hands-free mode.
- USB audio interface at 44.1 kHz.
- USB audio interface at 96 kHz, if available.
- Virtual or aggregate input such as Sokuji, BlackHole, or Loopback.

For each device:

- First dictation captures non-empty PCM.
- Two consecutive dictations both transcribe.
- Console shows `Audio recording engine bound` with the expected device.
- No `Format mismatch` or `Failed to create tap, config change pending!`.

## Formatter

- Dictate `does it feel like real-time processing?`; final text must remain a question.
- Dictate `Hello, can you hear me?`; final text must not contain prompt examples or `Options:`.
- Dictate a long input (≥ 100 sentences); the formatter should chunk the transcript and format each chunk through the helper. Final text should still read as clean dictation, and session tags should include `chunked-N` (with N matching `ceil(words/30)`).

## Long Running

- Leave manual recording running until the max duration is reached; it should stop through the normal stop path.
- Confirm the temp capture file is removed after transcription.

## Upgrade

- Install over a previous ExecuWhisper build.
- Verify the paste helper is upgraded and Accessibility can still be granted to `org.pytorch.executorch.ExecuWhisper.PasteHelper`.
