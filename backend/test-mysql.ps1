param(
    [string]$MySqlBase = 'C:\Program Files\MySQL\MySQL Server 8.0',
    [string]$JavaHome = 'C:\Program Files\Java\jdk-17',
    [string]$Maven = 'mvn.cmd',
    [string]$TestRoot = "$env:PUBLIC\Documents\campus-platform-tests"
)

$ErrorActionPreference = 'Stop'
$testDirectory = Join-Path $TestRoot ('campus-mysql-test-' + [guid]::NewGuid().ToString('N'))
$mysqlServer = Join-Path $MySqlBase 'bin\mysqld.exe'
$mysqlClient = Join-Path $MySqlBase 'bin\mysql.exe'
$mysqlAdmin = Join-Path $MySqlBase 'bin\mysqladmin.exe'
$testServer = $null
$oldPassword = $env:MYSQL_PWD
$oldIntegrationUrl = $env:CAMPUS_INTEGRATION_URL
$oldIntegrationPassword = $env:CAMPUS_INTEGRATION_PASSWORD
$oldJava = $env:JAVA_HOME
$oldPath = $env:Path
$oldJavaOptions = $env:JAVA_TOOL_OPTIONS
$oldOutputEncoding = $OutputEncoding
$exitCode = 1

try {
    $probe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 33317)
    $probe.Start()
    $probe.Stop()
    New-Item -ItemType Directory -Path $testDirectory | Out-Null
    $dataDirectory = Join-Path $testDirectory 'data'
    $initialize = Start-Process -FilePath $mysqlServer -WindowStyle Hidden -PassThru -Wait -ArgumentList @(
        '--no-defaults', '--initialize-insecure', "--basedir=`"$MySqlBase`"", "--datadir=`"$dataDirectory`""
    ) -RedirectStandardOutput (Join-Path $testDirectory 'initialize.out.log') -RedirectStandardError (Join-Path $testDirectory 'initialize.err.log')
    if ($initialize.ExitCode -ne 0) { throw "MySQL initialization failed. Logs: $testDirectory" }
    $testServer = Start-Process -FilePath $mysqlServer -WindowStyle Hidden -PassThru -ArgumentList @(
        '--no-defaults', '--console', "--basedir=`"$MySqlBase`"", "--datadir=`"$dataDirectory`"", '--port=33317', '--bind-address=127.0.0.1', '--mysqlx=OFF', '--skip-log-bin'
    ) -RedirectStandardOutput (Join-Path $testDirectory 'server.out.log') -RedirectStandardError (Join-Path $testDirectory 'server.err.log')
    $env:MYSQL_PWD = $null
    $ready = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        if ($testServer.HasExited) { throw "Test MySQL exited. Logs: $testDirectory" }
        & $mysqlAdmin --protocol=TCP -h 127.0.0.1 -P 33317 -u root --connect-timeout=1 ping 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'Test MySQL did not become ready' }
    $testPassword = [guid]::NewGuid().ToString('N')
    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    "ALTER USER 'root'@'localhost' IDENTIFIED BY '$testPassword';" | & $mysqlClient --protocol=TCP -h 127.0.0.1 -P 33317 -u root
    if ($LASTEXITCODE -ne 0) { throw 'Cannot secure temporary MySQL account' }
    $env:MYSQL_PWD = $testPassword
    $projectRoot = Split-Path -Parent $PSScriptRoot
    Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $projectRoot 'database\schema.sql') | & $mysqlClient --protocol=TCP -h 127.0.0.1 -P 33317 -u root --default-character-set=utf8mb4
    if ($LASTEXITCODE -ne 0) { throw 'Schema initialization failed' }
    Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $projectRoot 'database\testing\seed.sql') | & $mysqlClient --protocol=TCP -h 127.0.0.1 -P 33317 -u root --default-character-set=utf8mb4
    if ($LASTEXITCODE -ne 0) { throw 'Test fixture initialization failed' }
    'ALTER TABLE campus_platform.goods DROP COLUMN delivery_mode, DROP COLUMN delivery_note, DROP COLUMN bargaining_allowed; ALTER TABLE campus_platform.lost_found DROP COLUMN audit_status;' | & $mysqlClient --protocol=TCP -h 127.0.0.1 -P 33317 -u root
    if ($LASTEXITCODE -ne 0) { throw 'Cannot prepare legacy schema fixture' }
    foreach ($iteration in 1..2) {
        foreach ($migration in '20260911_delivery.sql', '20260911_reviews.sql', '20260911_lost_audit.sql', '20260911_media_wallet.sql', '20260911_xianggou.sql') {
            Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $projectRoot ('database\migrations\' + $migration)) | & $mysqlClient --protocol=TCP -h 127.0.0.1 -P 33317 -u root --default-character-set=utf8mb4
            if ($LASTEXITCODE -ne 0) { throw "Migration failed: $migration" }
        }
    }
    $env:CAMPUS_INTEGRATION_URL = 'jdbc:mysql://127.0.0.1:33317/campus_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
    $env:CAMPUS_INTEGRATION_PASSWORD = $testPassword
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome\bin;$env:Path"
    $env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'
    & $Maven -B -ntp -f (Join-Path $PSScriptRoot 'pom.xml') verify
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) { throw 'Maven verification failed' }
} finally {
    if ($testServer -and -not $testServer.HasExited) {
        & $mysqlAdmin --protocol=TCP -h 127.0.0.1 -P 33317 -u root --connect-timeout=2 shutdown 2>$null | Out-Null
        if (-not $testServer.WaitForExit(5000)) { $testServer.Kill() }
    }
    $env:MYSQL_PWD = $oldPassword
    $env:CAMPUS_INTEGRATION_URL = $oldIntegrationUrl
    $env:CAMPUS_INTEGRATION_PASSWORD = $oldIntegrationPassword
    $env:JAVA_HOME = $oldJava
    $env:Path = $oldPath
    $env:JAVA_TOOL_OPTIONS = $oldJavaOptions
    $OutputEncoding = $oldOutputEncoding
    Write-Output "Temporary MySQL logs retained at: $testDirectory"
}
exit $exitCode
