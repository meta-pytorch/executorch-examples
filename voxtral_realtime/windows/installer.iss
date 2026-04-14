; Voxtral Realtime Windows Installer - Inno Setup Script
; Bundles app + runner. Model weights are downloaded automatically on first launch.
; Download Inno Setup from https://jrsoftware.org/isinfo.php
;
; Before building the installer:
;   1. Build the runner from the ExecuTorch repo (cmake --preset voxtral-realtime-cuda)
;   2. Publish the app (dotnet publish ... -o publish)
;   3. Set the EXECUTORCH_ROOT environment variable or edit the path below
;   4. Run: ISCC.exe installer.iss

#ifndef ExecuTorchRoot
  #define ExecuTorchRoot GetEnv("EXECUTORCH_ROOT")
  #if ExecuTorchRoot == ""
    #error "Set EXECUTORCH_ROOT environment variable to your ExecuTorch repo path, or pass /DExecuTorchRoot=C:\path\to\executorch to ISCC.exe"
  #endif
#endif
#define RunnerDir ExecuTorchRoot + "\cmake-out\examples\models\voxtral_realtime\Release"

[Setup]
AppName=Voxtral Realtime
AppVersion=1.0.0
AppPublisher=Meta Platforms
AppPublisherURL=https://github.com/meta-pytorch/executorch-examples
DefaultDirName={autopf}\VoxtralRealtime
DefaultGroupName=Voxtral Realtime
OutputDir=installer-output
OutputBaseFilename=VoxtralRealtime-Setup
SetupIconFile=VoxtralRealtime\VoxtralRealtime\Resources\app.ico
UninstallDisplayIcon={app}\VoxtralRealtime.exe
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest
DiskSpanning=no

[Files]
; App executable
Source: "VoxtralRealtime\publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs

; Runner binary + CUDA shims
Source: "{#RunnerDir}\voxtral_realtime_runner.exe"; DestDir: "{app}\runner"; Flags: ignoreversion
Source: "{#RunnerDir}\aoti_cuda_shims.dll"; DestDir: "{app}\runner"; Flags: ignoreversion

; Model weights are NOT bundled — they are downloaded from HuggingFace on first launch.

[Icons]
Name: "{group}\Voxtral Realtime"; Filename: "{app}\VoxtralRealtime.exe"
Name: "{autodesktop}\Voxtral Realtime"; Filename: "{app}\VoxtralRealtime.exe"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"

[Run]
Filename: "{app}\VoxtralRealtime.exe"; Description: "Launch Voxtral Realtime"; Flags: nowait postinstall skipifsilent
