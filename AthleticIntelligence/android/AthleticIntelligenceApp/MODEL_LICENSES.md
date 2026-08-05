<!--
Copyright (c) Meta Platforms, Inc. and affiliates.
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree.
-->

# RTMW Model Notice

The BSD-style repository license does not replace the terms for the RTMW model, weights, or training data. Anyone distributing this app or its artifacts is responsible for reviewing the current upstream terms.

## RTMW whole-body pose

- Model family: RTMW-l, 133-keypoint whole-body pose, SimCC output, 384 x 288 input.
- Project and attribution: OpenMMLab MMPose, RTMPose/RTMW project, <https://github.com/open-mmlab/mmpose/tree/main/projects/rtmpose>.
- Pinned model archive: <https://download.openmmlab.com/mmpose/v1/projects/rtmw/onnx_sdk/rtmw-dw-x-l_simcc-cocktail14_270e-384x288_20231122.zip>.
- MMPose source license: Apache License 2.0, <https://github.com/open-mmlab/mmpose/blob/main/LICENSE>.

The downloaded archive identifies MMPose 1.2.0 and an RTMW-l cocktail training configuration. Model weights can also be affected by the terms of their training datasets. Confirm those terms for the intended use; this notice does not grant additional rights to the weights or datasets.

The `app/src/main/assets/rtmw_l_int8.pte` artifact is a quantized ExecuTorch conversion. Its verified local SHA-256 is `fdcae93de58d38499c0d72ef2c80b89f9fb6e57ae9533ca3a3e871c3a21e1345`.

## Instrumentation fixture

`app/src/androidTest/assets/rtmw_pose_fixture.jpg` is copied from the MMPose
test data at
<https://github.com/open-mmlab/mmpose/blob/main/tests/data/coco/000000000785.jpg>.
Its SHA-256 is
`83981537a7baeafbeb9c8cb67b3484dc26433f574b3685d021fa537e277e4726`.
The image is used only for device-side pose smoke testing. Review the upstream
MMPose and COCO dataset terms before redistribution.
