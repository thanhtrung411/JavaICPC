param(
    [switch]$CheckOnly
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
    # Older consoles may reject changing the encoding; the runner can continue.
}

function Write-Info($message) {
    Write-Host "[INFO] $message" -ForegroundColor Cyan
}

function Write-Ok($message) {
    Write-Host "[OK] $message" -ForegroundColor Green
}

function Write-Warn($message) {
    Write-Host "[WARN] $message" -ForegroundColor Yellow
}

function Write-Fail($message) {
    Write-Host "[FAIL] $message" -ForegroundColor Red
}

function Test-CommandExists($command) {
    return $null -ne (Get-Command $command -ErrorAction SilentlyContinue)
}

function Get-VersionLine($command, $arguments) {
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $command @arguments 2>&1
        return ($output | Select-Object -First 1)
    } finally {
        $ErrorActionPreference = $oldErrorAction
    }
}

function Read-AppProperties($path) {
    $props = @{}
    if (-not (Test-Path $path)) {
        return $props
    }

    Get-Content $path | ForEach-Object {
        $line = $_.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith("#")) {
            return
        }

        $idx = $line.IndexOf("=")
        if ($idx -le 0) {
            return
        }

        $key = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        $props[$key] = $value
    }

    return $props
}

$requiredFiles = @(
    "src\Main.java",
    "src\UI\Main.java",
    "src\UI\MainUI.java",
    "src\Service\AIService.java",
    "src\Service\DbService.java",
    "src\Service\LocalJudgeService.java",
    "lib\sqlite-jdbc-3.45.3.0.jar",
    "lib\slf4j-api-2.0.13.jar",
    "lib\slf4j-simple-2.0.13.jar"
)

$missing = New-Object System.Collections.Generic.List[string]

Write-Info "Kiem tra moi truong JavaICPC..."

foreach ($file in $requiredFiles) {
    if (-not (Test-Path $file)) {
        $missing.Add($file)
    }
}

if (-not (Test-CommandExists "java")) {
    $missing.Add("java trong PATH")
}

if (-not (Test-CommandExists "javac")) {
    $missing.Add("javac trong PATH (can cai JDK, khong chi JRE)")
}

if ($missing.Count -gt 0) {
    Write-Fail "Thieu thanh phan bat buoc:"
    foreach ($item in $missing) {
        Write-Host "  - $item" -ForegroundColor Red
    }
    Write-Host ""
    Write-Host "Hay cai JDK va kiem tra lai thu muc lib/src truoc khi chay." -ForegroundColor Red
    Read-Host "Nhan Enter de thoat"
    exit 1
}

Write-Ok "Day du file source va thu vien bat buoc."

$javaVersion = Get-VersionLine "java" @("-version")
$javacVersion = Get-VersionLine "javac" @("-version")
Write-Ok $javaVersion
Write-Ok $javacVersion

if (Test-CommandExists "g++") {
    $gppVersion = Get-VersionLine "g++" @("--version")
    Write-Ok "g++ da san sang: $gppVersion"
} else {
    Write-Warn "Khong tim thay g++ trong PATH. App van chay, nhung chuc nang cham C++ se khong hoat dong."
}

if (-not (Test-Path "data")) {
    New-Item -ItemType Directory -Path "data" | Out-Null
    Write-Ok "Da tao thu muc data."
}

if (-not (Test-Path "data\app.properties")) {
    Write-Warn "Chua co data\app.properties. Ban can cau hinh DB/AI trong tab Cau hinh sau khi mo app."
} else {
    $props = Read-AppProperties "data\app.properties"
    $dbUrl = $props["db.url"]
    $projectId = $props["google.projectId"]
    $location = $props["google.location"]
    $authType = $props["google.authType"]
    $credential = $props["google.credential"]
    $serviceAccountJsonPath = $props["google.serviceAccountJsonPath"]
    $aiModel = $props["ai.model"]

    if ([string]::IsNullOrWhiteSpace($dbUrl)) {
        Write-Warn "data\app.properties chua co db.url. App van mo duoc, nhung nen cau hinh DB trong tab Cau hinh."
    }

    $hasCredential = -not [string]::IsNullOrWhiteSpace($credential)
    if ($authType -eq "Service Account JSON") {
        $hasCredential = $hasCredential -or (-not [string]::IsNullOrWhiteSpace($serviceAccountJsonPath))
    }

    if ([string]::IsNullOrWhiteSpace($projectId) -or [string]::IsNullOrWhiteSpace($location) -or [string]::IsNullOrWhiteSpace($authType) -or [string]::IsNullOrWhiteSpace($aiModel) -or -not $hasCredential) {
        Write-Warn "Cau hinh AI chua day du. App van mo duoc, nhung cac nut dung AI co the bao loi den khi cau hinh lai."
    }
}

Write-Info "Bien dich chuong trinh..."

$outDir = "out\run"
if (-not (Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir | Out-Null
}

$sources = @(
    "src\Main.java"
) + (Get-ChildItem -Path "src\UI" -Filter "*.java" | ForEach-Object { $_.FullName }) + @(
    "src\Service\DbService.java",
    "src\Service\AIService.java",
    "src\Service\LocalJudgeService.java"
)

& javac -encoding UTF-8 -cp "lib/*" -d $outDir $sources

if ($LASTEXITCODE -ne 0) {
    Write-Fail "Bien dich that bai. Hay xem loi javac o tren."
    Read-Host "Nhan Enter de thoat"
    exit $LASTEXITCODE
}

Write-Ok "Bien dich thanh cong."

if ($CheckOnly) {
    Write-Ok "CheckOnly hoan tat. Moi thu bat buoc da san sang, khong chay ung dung."
    exit 0
}

Write-Info "Dang mo ung dung..."

& java -cp "$outDir;lib/*" UI.Main

if ($LASTEXITCODE -ne 0) {
    Write-Fail "Ung dung da thoat voi ma loi $LASTEXITCODE."
    Read-Host "Nhan Enter de thoat"
    exit $LASTEXITCODE
}
