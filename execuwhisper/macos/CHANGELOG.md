# ExecuWhisper Changelog

## v0.1.0 — Initial open-source release

- Initial open-source publication of the ExecuWhisper macOS dictation app.
- Apple Silicon-only (M1+); requires macOS 14.0 or newer.
- ASR: NVIDIA Parakeet-TDT via the Metal backend (executorch helper from
  pytorch/executorch#18861).
- Formatter: fine-tuned LFM2.5-350M via the MLX delegate (executorch helper
  from pytorch/executorch#19562; export pipeline from #19195).
- Models distributed via Hugging Face Hub:
  - `younghan-meta/Parakeet-TDT-ExecuTorch-Metal`
  - `younghan-meta/LFM2.5-350M-ExecuWhisper-Formatter`
- AMI release-gate eval for the formatter: forbidden 0.030 ≤ 0.10,
  coverage 0.874 ≥ 0.85 (RELEASE-READY).
- Build via `xcodegen generate` + `xcodebuild`. Set `DEVELOPMENT_TEAM` to
  your Apple Developer team via env var; the project no longer hard-codes
  a team identifier.
- Helpers signed with the hardened runtime + `disable-library-validation`
  + `allow-dyld-environment-variables` entitlements so they can load the
  user-supplied `libomp.dylib` (install with `brew install libomp`).
