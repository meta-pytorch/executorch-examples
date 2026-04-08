; Voxtral Realtime Windows Installer - Inno Setup Script
; Download Inno Setup from https://jrsoftware.org/isinfo.php

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

[Files]
Source: "VoxtralRealtime\publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs

[Icons]
Name: "{group}\Voxtral Realtime"; Filename: "{app}\VoxtralRealtime.exe"
Name: "{autodesktop}\Voxtral Realtime"; Filename: "{app}\VoxtralRealtime.exe"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"

[Run]
Filename: "{app}\VoxtralRealtime.exe"; Description: "Launch Voxtral Realtime"; Flags: nowait postinstall skipifsilent
