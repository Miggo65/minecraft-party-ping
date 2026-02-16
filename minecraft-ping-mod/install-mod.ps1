param(
    [string]$JarPath
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

if ([string]::IsNullOrWhiteSpace($JarPath)) {
    $candidate = Get-ChildItem -Path (Join-Path $scriptDir 'build\libs\*.jar') -File |
        Where-Object { $_.Name -notlike '*-sources.jar' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $candidate) {
        throw "Kein Mod-JAR gefunden. Erst bauen (z.B. gradle build) oder -JarPath angeben."
    }

    $JarPath = $candidate.FullName
}

if (-not (Test-Path $JarPath)) {
    throw "JAR nicht gefunden: $JarPath"
}

$modsDir = 'C:\Users\mikov\.lunarclient\profiles\vanilla\1.21\mods\fabric-1.21.11'
New-Item -ItemType Directory -Path $modsDir -Force | Out-Null

Copy-Item -Path $JarPath -Destination $modsDir -Force

Write-Host "Mod installiert: $JarPath -> $modsDir"
Write-Host "Starte Minecraft mit Fabric Loader 1.21.11 + Fabric API."
