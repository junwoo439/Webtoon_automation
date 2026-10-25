$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot

try {
    if ($projectRoot -match '[^\x00-\x7F]') {
        throw 'Move the project to an English-only path, for example C:\toonpace, and try again.'
    }
    $javaCandidates = @(
        "$env:ProgramFiles\Android\Android Studio\jbr",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr",
        $env:JAVA_HOME
    )
    $javaDir = $javaCandidates | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe')) } | Select-Object -First 1
    if (-not $javaDir) { throw 'Install Android Studio first: https://developer.android.com/studio' }
    $env:JAVA_HOME = $javaDir
    $env:Path = "$javaDir\bin;$env:Path"
    $sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk")
    $sdkDir = $sdkCandidates | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -First 1
    if (-not $sdkDir) { throw 'Open Android Studio and finish its Standard setup to install the Android SDK.' }
    $env:ANDROID_HOME = $sdkDir
    $env:ANDROID_SDK_ROOT = $sdkDir
    Write-Host 'Step 1/3: Checking the SDK installed by Android Studio.'
    if (-not (Test-Path -LiteralPath (Join-Path $sdkDir 'platforms\android-35\android.jar')) -or
        -not (Test-Path -LiteralPath (Join-Path $sdkDir 'build-tools\35.0.0\aapt2.exe'))) {
        throw 'In Android Studio > SDK Manager, install Android SDK Platform 35 and Build-Tools 35.0.0, then retry. This script does not invoke sdkmanager.'
    }

    Write-Host 'Step 2/3: Downloading Gradle 8.11.1 from its official distribution service.'
    $toolDir = Join-Path $projectRoot '.tooling'
    New-Item -ItemType Directory -Path $toolDir -Force | Out-Null
    $gradle = Join-Path $toolDir 'gradle-8.11.1\bin\gradle.bat'
    if (-not (Test-Path -LiteralPath $gradle)) {
        $archive = Join-Path $toolDir 'gradle-8.11.1-bin.zip'
        $checksum = Join-Path $toolDir 'gradle-8.11.1-bin.zip.sha256'
        Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' -OutFile $archive
        Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256' -OutFile $checksum
        $expected = (Get-Content -LiteralPath $checksum -Raw).Trim().ToLowerInvariant()
        $actual = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne $expected) { throw 'Gradle checksum mismatch. Download interrupted or invalid.' }
        Expand-Archive -LiteralPath $archive -DestinationPath $toolDir -Force
    }

    Write-Host 'Step 3/3: Building the APK. The first build downloads dependencies.'
    & $gradle --no-daemon --console=plain :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed. Share the error lines above for diagnosis.' }
    $builtApk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path -LiteralPath $builtApk)) { throw 'Build ended without an APK.' }
    $output = Join-Path $projectRoot 'output'
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    Copy-Item -LiteralPath $builtApk -Destination (Join-Path $output 'toonpace.apk') -Force
    Write-Host "APK ready: $output\toonpace.apk"
    Start-Process explorer.exe -ArgumentList ('"' + $output + '"')
    exit 0
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
