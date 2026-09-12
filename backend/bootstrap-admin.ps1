param([string]$MySql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$config = ConvertFrom-StringData (Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'src\main\resources\application.properties'))
$connection = [uri]($config['spring.datasource.url'] -replace '^jdbc:', '')
$databaseName = $connection.AbsolutePath.TrimStart('/')
$oldPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $config['spring.datasource.password']
$arguments = @('--protocol=TCP', '-h', $connection.Host, '-P', $connection.Port, '-u', $config['spring.datasource.username'], '--default-character-set=utf8mb4', $databaseName)
try {
    $adminCount = & $MySql @arguments -N -e "select count(*) from sys_user where role='ADMIN'"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read administrator status' }
    if ([int]$adminCount -gt 0) { Write-Output 'Administrator already exists; no credentials changed.'; exit 0 }
    $password = [guid]::NewGuid().ToString('N')
    $salt = [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(16)
    $hash = [System.Security.Cryptography.Rfc2898DeriveBytes]::Pbkdf2($password, $salt, 120000, [System.Security.Cryptography.HashAlgorithmName]::SHA256, 32)
    $encoded = 'pbkdf2$' + [Convert]::ToBase64String($salt) + '$' + [Convert]::ToBase64String($hash)
    $localDirectory = Join-Path $projectRoot '.local'
    New-Item -ItemType Directory -Path $localDirectory -Force | Out-Null
    $accountFile = Join-Path $localDirectory 'admin-credentials.txt'
    if (Test-Path -LiteralPath $accountFile) { throw 'Credentials file already exists; inspect it before retrying.' }
    [IO.File]::WriteAllText($accountFile, "Username: admin`r`nPassword: $password`r`n", [Text.UTF8Encoding]::new($false))
    "insert into sys_user(username,password_hash,nickname,role) values('admin','$encoded','Administrator','ADMIN');" | & $MySql @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Administrator creation failed; generated credentials are not active.' }
    Write-Output "Administrator created. Credentials saved locally: $accountFile"
} finally { $env:MYSQL_PWD = $oldPassword }
