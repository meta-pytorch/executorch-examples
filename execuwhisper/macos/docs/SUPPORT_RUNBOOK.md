# ExecuWhisper Support Runbook

## Logs

Default logs hide transcript contents.

```bash
log stream --predicate 'subsystem == "org.pytorch.executorch.ExecuWhisper"' --info
```

To debug transcript contents on a local machine:

```bash
defaults write org.pytorch.executorch.ExecuWhisper EXECUWHISPER_DEBUG_LOG_TRANSCRIPTS -bool YES
```

Disable it after diagnosis:

```bash
defaults delete org.pytorch.executorch.ExecuWhisper EXECUWHISPER_DEBUG_LOG_TRANSCRIPTS
```

## Reset Permissions

```bash
tccutil reset Microphone org.pytorch.executorch.ExecuWhisper
tccutil reset Accessibility org.pytorch.executorch.ExecuWhisper.PasteHelper
```

## Reset Model Cache

```bash
rm -rf "$HOME/Library/Application Support/ExecuWhisper/models"
```

The app will download models again on next launch.

## Common Symptoms

### No audio captured

Check for:

- `Audio recording engine bound`
- `Format mismatch`
- `Failed to create tap, config change pending!`
- selected microphone UID and device name

If the selected device is virtual or Bluetooth, retry with built-in mic to isolate routing.

### Auto-paste does not work

Grant Accessibility to `ExecuWhisper Paste Helper`, not only to `ExecuWhisper`.

### Formatter returns wrong text

Look for:

- `formatter-fallback`
- `chunked-N` and `partial-fallback-K` (long inputs are formatted in 30-word chunks; `partial-fallback-K` flags how many chunks the validator rejected and substituted with the raw chunk text)
- `LFM2.5 output rejected by validator`

With transcript debug logging disabled, support logs should not contain dictated text.
