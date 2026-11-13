# Arranca la aplicación en modo desarrollo.
# Carga las variables de entorno de usuario (DB_PASSWORD, ADMIN_*, REMEMBER_ME_KEY...) aunque esta
# terminal se abriera antes de definirlas con setx.
#
#   powershell -ExecutionPolicy Bypass -File scripts\arrancar.ps1

$variables = 'DB_URL', 'DB_USER', 'DB_PASSWORD', 'ADMIN_USERNAME', 'ADMIN_PASSWORD', 'REMEMBER_ME_KEY',
             'APP_PORT', 'APP_STORAGE_PATH', 'APP_MAX_FILE_SIZE', 'APP_MAX_REQUEST_SIZE',
             'DOCSCANNER_URL', 'DOCSCANNER_CONNECT_TIMEOUT', 'DOCSCANNER_REQUEST_TIMEOUT',
             'DOCSCANNER_MAX_RESPONSE_SIZE', 'APP_SCAN_TEMP_PATH', 'APP_SCAN_TEMP_TTL',
             'APP_SCAN_TEMP_MAX_SIZE', 'APP_SCAN_TEMP_MAX_PER_USER', 'APP_SCAN_TEMP_MAX_TOTAL'
foreach ($v in $variables) {
    $valor = [Environment]::GetEnvironmentVariable($v, 'User')
    if ($valor) { Set-Item -Path "Env:$v" -Value $valor }
}
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
}

Set-Location (Join-Path $PSScriptRoot '..')
& .\mvnw.cmd spring-boot:run
