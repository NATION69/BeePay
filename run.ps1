param(
    [switch]$BuildApk,
    [switch]$StartAgentB,
    [switch]$ServeApk,
    [int]$AgentBPort = 8080,
    [int]$ApkPort = 8090
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$agentA = Join-Path $root "agent-a"
$agentB = Join-Path $root "agent-b"
$downloadFile = Join-Path $root "download"

function Write-DownloadFile {
    param(
        [string]$Url,
        [string]$ApkPath
    )

    $content = @(
        "BeePay Download Link"
        "Updated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
        ""
        "URL:"
        "$Url"
        ""
        "APK:"
        "$ApkPath"
    ) -join "`r`n"

    Set-Content -Path $downloadFile -Value $content -NoNewline
    Write-Host "Updated download file: $downloadFile"
}

function Get-LanIPv4 {
    $ip = Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object {
            $_.IPAddress -notlike "169.254.*" -and
            $_.IPAddress -ne "127.0.0.1" -and
            $_.InterfaceAlias -notmatch "Loopback|vEthernet"
        } |
        Select-Object -First 1 -ExpandProperty IPAddress

    if (-not $ip) {
        throw "No LAN IPv4 address found."
    }

    return $ip
}

function Find-LatestApk {
    $apks = Get-ChildItem -Path (Join-Path $agentA "app\build\outputs") -Recurse -File -Filter *.apk -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending

    if (-not $apks -or $apks.Count -eq 0) {
        return $null
    }

    return $apks[0]
}

if ($BuildApk) {
    Write-Host "Building APK..."

    $gradleCmd = Get-Command gradle -ErrorAction SilentlyContinue
    if (-not $gradleCmd) {
        Write-Host "Global Gradle not found. Build from Android Studio:"
        Write-Host "Build > Build APK(s)"
    } else {
        & gradle -p $agentA assembleDebug
    }
}

if ($StartAgentB) {
    Write-Host "Starting Agent B on port $AgentBPort..."

    $envFile = Join-Path $agentB ".env"
    $envExample = Join-Path $agentB ".env.example"
    if (-not (Test-Path $envFile) -and (Test-Path $envExample)) {
        Copy-Item $envExample $envFile
        Write-Host "Created $envFile from .env.example"
    }

    $nodeCmd = Get-Command node -ErrorAction SilentlyContinue
    $npmCmd = Get-Command npm -ErrorAction SilentlyContinue
    if (-not $nodeCmd -or -not $npmCmd) {
        Write-Host "Node/npm not found. Install Node.js then run this again."
    } else {
        Start-Process powershell -ArgumentList @(
            "-NoExit",
            "-Command",
            "Set-Location '$agentB'; if (!(Test-Path 'node_modules')) { npm install }; `$env:PORT='$AgentBPort'; npm run start"
        ) | Out-Null
        Write-Host "Agent B started in a new terminal window."
    }
}

if ($ServeApk) {
    $apk = Find-LatestApk
    if (-not $apk) {
        Write-Host "No APK found. Build first."
        Write-Host "Try: .\run.ps1 -BuildApk"
    } else {
        $ip = Get-LanIPv4
        $apkDir = Split-Path -Parent $apk.FullName
        $url = "http://${ip}:$ApkPort/$($apk.Name)"

        Start-Process powershell -ArgumentList @(
            "-NoExit",
            "-Command",
            "Set-Location '$apkDir'; python -m http.server $ApkPort"
        ) | Out-Null

        Write-Host "APK server started in a new terminal window."
        Write-Host "Download URL: $url"

        Write-DownloadFile -Url $url -ApkPath $apk.FullName
    }
}

if (-not $BuildApk -and -not $StartAgentB -and -not $ServeApk) {
    Write-Host "Usage:"
    Write-Host "  .\run.ps1 -BuildApk"
    Write-Host "  .\run.ps1 -StartAgentB"
    Write-Host "  .\run.ps1 -ServeApk"
    Write-Host "  .\run.ps1 -BuildApk -StartAgentB -ServeApk"
}
