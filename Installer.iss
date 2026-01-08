[Setup]
AppName=GothMessenger
AppVersion=1.0.0
DefaultDirName={pf}\GothMessenger
DefaultGroupName=GothMessenger
UninstallDisplayIcon={app}\GothMessenger.exe
Compression=lzma2
SolidCompression=yes
OutputDir=userdocs:Inno Setup Output
OutputBaseFilename=GothMessenger_Setup
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
SetupIconFile=Assets\icon.ico

[Files]
Source: "Build\GothMessenger.exe"; DestDir: "{app}"
Source: "Build\*.dll"; DestDir: "{app}"
Source: "Build\runtimes\*"; DestDir: "{app}\runtimes"; Flags: recursesubdirs
Source: "Assets\icon.ico"; DestDir: "{app}"

[Icons]
Name: "{group}\GothMessenger"; Filename: "{app}\GothMessenger.exe"
Name: "{group}\Удалить GothMessenger"; Filename: "{uninstallexe}"
Name: "{commondesktop}\GothMessenger"; Filename: "{app}\GothMessenger.exe"

[Run]
Filename: "{app}\GothMessenger.exe"; Description: "Запустить GothMessenger"; Flags: postinstall nowait

[Code]
procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    // Добавление в автозагрузку
    RegWriteStringValue(
      HKEY_CURRENT_USER,
      'Software\Microsoft\Windows\CurrentVersion\Run',
      'GothMessenger',
      ExpandConstant('{app}\GothMessenger.exe --minimized')
    );
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usPostUninstall then
  begin
    // Удаление из автозагрузки
    RegDeleteValue(
      HKEY_CURRENT_USER,
      'Software\Microsoft\Windows\CurrentVersion\Run',
      'GothMessenger'
    );
  end;
end;