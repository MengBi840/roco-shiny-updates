# 发布脚本：把 APK 上传到 GitHub Releases（token 可从环境变量或 E:\D4HWORK\.gh_token 读取）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File publish_release.ps1 -Version 1.5 -Notes "这里写更新公告"
#       （默认不加 -Notes 时，公告正文留空，由你稍后到 GitHub Release 里自行填写，App 会显示为“更新公告”）
param(
    [string]$Version = "1.4",
    [string]$ApkPath = "E:\D4HWORK\RocoShinyDex\app\build\outputs\apk\release\app-release.apk",
    [string]$Notes = ""
)
$ErrorActionPreference = 'Stop'
$OWNER = "MengBi840"
$REPO  = "roco-shiny-updates"
$token = $env:GITHUB_TOKEN
if (-not $token) {
    $tokFile = "E:\D4HWORK\.gh_token"
    if (Test-Path $tokFile) { $token = (Get-Content $tokFile -Raw).Trim() }
}
if (-not $token) { throw "请先设置环境变量 GITHUB_TOKEN，或在 E:\D4HWORK\.gh_token 中保存 token" }
if (-not (Test-Path $ApkPath)) { throw "找不到 APK：$ApkPath" }
$api = "https://api.github.com"
$headers = @{ Authorization = "token $token"; "User-Agent" = "roco-updater"; Accept = "application/vnd.github+json" }

# 1) 确保仓库存在（不存在则创建公开仓库）
$repoUrl = "$api/repos/$OWNER/$REPO"
try {
    Invoke-RestMethod -Uri $repoUrl -Method Get -Headers $headers | Out-Null
    Write-Output "仓库已存在：$REPO"
} catch {
    $body = @{ name = $REPO; description = "洛克精灵册 自动更新源"; private = $false } | ConvertTo-Json
    try {
        Invoke-RestMethod -Uri "$api/user/repos" -Method Post -Headers $headers -ContentType "application/json" -Body $body | Out-Null
        Write-Output "已创建公开仓库：$REPO"
    } catch { throw "创建仓库失败：$($_.Exception.Message)" }
}

# 2) 创建 Release（公告正文默认留空——由发布者自行填写，App 会把它显示为“更新公告”）
$tag = "v$Version"
$relBody = @{ tag_name = $tag; name = $tag; body = [string]$Notes; draft = $false; prerelease = $false } | ConvertTo-Json
$release = Invoke-RestMethod -Uri "$api/repos/$OWNER/$REPO/releases" -Method Post -Headers $headers -ContentType "application/json" -Body $relBody
Write-Output "已创建 Release：$tag (id=$($release.id))"

# 3) 上传 APK 资产（尽量用 ASCII 名称，避免被 GitHub 截断）
$assetName = "RocoDex-v$Version.apk"
$uploadUrl = "https://uploads.github.com/repos/$OWNER/$REPO/releases/$($release.id)/assets?name=$([uri]::EscapeDataString($assetName))"
$result = Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers @{ Authorization = "token $token"; "User-Agent" = "roco-updater"; Accept = "application/vnd.github+json"; "Content-Type" = "application/octet-stream" } -InFile $ApkPath
Write-Output "上传完成：$($result.browser_download_url)"
Write-Output "发布成功。App 将在启动时检测到 v$Version 并提示更新。"
