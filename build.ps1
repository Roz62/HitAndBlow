# HitAndBlow を javac で直接ビルドする（Gradle 不要・オフライン）
#   使い方:  powershell -ExecutionPolicy Bypass -File build.ps1 [-Deploy]
#   -Deploy を付けると、できた jar をテストサーバーの plugins に上書きコピーする
# コンパイル用の Paper API は、テストサーバーの libraries フォルダから読む。
param(
    [switch]$Deploy,
    [string]$Server = $(if ($env:PAPER_SERVER) { $env:PAPER_SERVER } else { 'C:\paper-server' })
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

# JDK の場所: JAVA_HOME → 既定のインストール先 → PATH
$jdk = if ($env:JAVA_HOME) { "$env:JAVA_HOME\bin" } else { 'C:\Program Files\Eclipse Adoptium\jdk-21.0.3.9-hotspot\bin' }
$javac = if (Test-Path "$jdk\javac.exe") { "$jdk\javac.exe" } else { 'javac' }
$jar   = if (Test-Path "$jdk\jar.exe")   { "$jdk\jar.exe" }   else { 'jar' }

$version = (Select-String -Path "$root\src\main\resources\plugin.yml" -Pattern '^version:\s*(.+)$').Matches[0].Groups[1].Value.Trim()
$build   = "$root\build"
$classes = "$build\classes"
$out     = "$build\libs\HitAndBlow-$version.jar"

if (Test-Path $classes) { Remove-Item -Recurse -Force $classes }
New-Item -ItemType Directory -Force $classes, "$build\libs" | Out-Null

# クラスパスとソース一覧は引数ファイルで渡す（コマンドラインの長さ制限対策）
# Vault API はテストサーバーの plugins\Vault.jar から読む（jar には同梱しない）
$vault = Get-ChildItem "$Server\plugins" -Filter 'Vault*.jar' | Select-Object -First 1
if (-not $vault) { throw "Vault の jar が $Server\plugins に見つかりません" }
$cp = ((Get-ChildItem "$Server\libraries" -Recurse -Filter *.jar | ForEach-Object { $_.FullName }) + $vault.FullName) -join ';'
$sources = Get-ChildItem "$root\src\main\java" -Recurse -Filter *.java | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
$argFile = "$build\javac.args"
@('-encoding', 'UTF-8', '--release', '21', '-proc:none', '-d', ('"' + ($classes -replace '\\', '/') + '"'),
  '-cp', ('"' + ($cp -replace '\\', '/') + '"')) + $sources | Set-Content -Encoding ascii $argFile

& $javac "@$argFile"
if ($LASTEXITCODE -ne 0) { throw "コンパイル失敗" }

Copy-Item "$root\src\main\resources\*" $classes -Recurse
if (Test-Path $out) { Remove-Item $out }
& $jar --create --file $out -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar 作成失敗" }
Write-Host "OK: $out"

if ($Deploy) {
    $dest = "$Server\plugins"
    Get-ChildItem $dest -Filter 'HitAndBlow-*.jar' | Remove-Item -Force
    Copy-Item $out $dest
    Write-Host "配置: $dest\$(Split-Path $out -Leaf)（サーバー再起動で反映）"
}
