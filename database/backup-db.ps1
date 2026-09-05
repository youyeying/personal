# 数据库定时备份脚本（个人记录系统）
# - 凭证从 backend/src/main/resources/application.properties 读取，不在脚本中硬编码
# - 输出：database/backups/personal_record-YYYYMMDD-HHmmss.sql.gz（7-Zip/gzip 压缩，无 gzip 时退化为 .sql）
# - 保留最近 14 份，超出自动清理
# - 手动执行：powershell -ExecutionPolicy Bypass -File database\backup-db.ps1
#   计划任务（每日 03:00）：见 README「数据库备份」章节（schtasks 命令）

$ErrorActionPreference = 'Stop'

# ---- 路径定位（脚本在 database/ 目录下） ----
$dbDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent $dbDir
$propsFile = Join-Path $rootDir 'backend\src\main\resources\application.properties'
$backupDir = Join-Path $dbDir 'backups'

if (-not (Test-Path $propsFile)) { Write-Error "找不到配置文件：$propsFile"; exit 1 }

# ---- 从 application.properties 读连接信息 ----
$props = @{}
Get-Content $propsFile | ForEach-Object {
    if ($_ -match '^\s*([^#=\s]+)\s*=\s*(.+)$') { $props[$Matches[1].Trim()] = $Matches[2].Trim() }
}
$dbUrl  = $props['spring.datasource.url']
$dbUser = $props['spring.datasource.username']
$dbPass = $props['spring.datasource.password']
if (-not $dbUrl) { Write-Error 'application.properties 缺少 spring.datasource.url'; exit 1 }

# 从 jdbc url 解析 host/port/库名：jdbc:mysql://host:3306/dbname?...
if ($dbUrl -notmatch 'jdbc:mysql://([^:/]+):(\d+)/([^?]+)') { Write-Error "无法解析数据库地址：$dbUrl"; exit 1 }
$dbHost = $Matches[1]; $dbPort = $Matches[2]; $dbName = $Matches[3]

# ---- 导出 ----
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outFile = Join-Path $backupDir "$dbName-$stamp.sql"
$gzFile = "$outFile.gz"

$env:MYSQL_PWD = $dbPass   # 环境变量传密码，避免命令行明文
try {
    & mysqldump -h $dbHost -P $dbPort -u $dbUser --single-transaction --routines --triggers --default-character-set=utf8mb4 --result-file="$outFile" $dbName
    if ($LASTEXITCODE -ne 0) { Write-Error 'mysqldump 导出失败'; exit 1 }

    # 压缩（gzip 可用时）
    $gzip = Get-Command gzip -ErrorAction SilentlyContinue
    if ($gzip) {
        & gzip -9 -f $outFile
        $finalFile = $gzFile
    } else {
        $finalFile = $outFile
    }
    $size = [math]::Round((Get-Item $finalFile).Length / 1KB, 1)
    Write-Host "[OK] 备份完成：$finalFile（${size} KB）"
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

# ---- 保留最近 14 份 ----
$old = Get-ChildItem $backupDir -Filter "$dbName-*.sql*" | Sort-Object LastWriteTime -Descending | Select-Object -Skip 14
foreach ($f in $old) {
    Remove-Item $f.FullName -Force
    Write-Host "[清理] 已删除过期备份：$($f.Name)"
}
