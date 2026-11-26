param([switch]$RevisionVisual, [string]$MySql = 'C:\Program Files\MySQL\MySQL Server 8.0',
    [string]$Tomcat = 'C:\Program Files\Apache Software Foundation\Tomcat 10.1',
    [string]$Jdk = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.21.10002-hotspot')
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'Ejecuta la prueba con PowerShell 7.' }
if (!(Test-Path -LiteralPath 'target\sigmud.war') -or !(Test-Path -LiteralPath 'target\test-classes\pe\gob\sigmud\service\ComprobarGestionMySql.class')) {
    throw 'Ejecuta Maven verify desde la raíz del proyecto antes de probar.'
}
$qa = Join-Path (Get-Location) ('target\qa-mysql-' + [guid]::NewGuid().ToString('N').Substring(0,8))
$port = 13306
$null = New-Item -ItemType Directory -Path $qa
& (Join-Path $mysql 'bin\mysqld.exe') '--no-defaults' '--initialize-insecure' "--basedir=$mysql" "--datadir=$qa" '--console' 2>$null
if ($LASTEXITCODE) { throw 'No se inicializó el directorio de pruebas' }
if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { throw 'Puerto de pruebas ocupado' }
$init = Join-Path $qa 'inicio-sesion-temporal.sql'
$rootPass = [guid]::NewGuid().ToString('N')
$testPass = [guid]::NewGuid().ToString('N')
[IO.File]::WriteAllText($init, "ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPass';" + [Environment]::NewLine)
$arguments = @('--no-defaults', ('--basedir="' + $mysql + '"'), ('--datadir="' + $qa + '"'),
    ('--init-file="' + $init + '"'), '--port=13306', '--bind-address=127.0.0.1', '--mysqlx=OFF',
    '--skip-log-bin', '--secure-file-priv=NULL', '--console')
$proc = $null
$variables = 'MYSQL_PWD', 'SIGMUD_DB_PASSWORD', 'SIGMUD_DB_URL', 'SIGMUD_DB_USER', 'SIGMUD_QA_CLAVE', 'JAVA_HOME'
$entornoOriginal = @{}
foreach ($variable in $variables) {
    $entornoOriginal[$variable] = [Environment]::GetEnvironmentVariable($variable, 'Process')
}
try {
    $proc = Start-Process -FilePath (Join-Path $mysql 'bin\mysqld.exe') -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $qa 'servicio.stdout') -RedirectStandardError (Join-Path $qa 'servicio.stderr')
    $env:MYSQL_PWD = $rootPass
    $env:SIGMUD_DB_PASSWORD = $testPass
    $env:SIGMUD_DB_URL = "jdbc:mysql://127.0.0.1:$port/sigmud_db"
    $env:SIGMUD_DB_USER = 'sigmud_app'
    $env:JAVA_HOME = $Jdk
    if (!$RevisionVisual -or [string]::IsNullOrWhiteSpace($env:SIGMUD_QA_CLAVE)) {
        $env:SIGMUD_QA_CLAVE = [guid]::NewGuid().ToString()
    }
    $cliente = Join-Path $mysql 'bin\mysql.exe'
    $opciones = @('--no-defaults', '--protocol=TCP', '--host=127.0.0.1', "--port=$port", '--user=root', '--ssl-mode=REQUIRED',
        '--default-character-set=utf8mb4', '--batch')
    $listo = $false
    for ($i = 0; $i -lt 30; $i++) {
        $proc.Refresh()
        if ($proc.HasExited) { throw 'No arrancó la instancia de pruebas' }
        & $cliente @opciones '--execute=SELECT VERSION()' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $listo = $true; break }
        Start-Sleep -Seconds 1
    }
    if (!$listo) { throw 'No respondió la instancia de pruebas' }
    Remove-Item -LiteralPath $init
    "CREATE DATABASE IF NOT EXISTS sigmud_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" | & $cliente @opciones
    if ($LASTEXITCODE) { throw 'No se creó el esquema aislado' }
    foreach ($nombre in @('01_esquema_sigmud.sql', '02_datos_iniciales.sql', '03_datos_demo.sql', '05_usuario_aplicacion.sql',
            '06_nombres_visibles.sql','07_permisos_sectores.sql','08_permisos_alta_citas.sql')) {
        $archivo = Join-Path (Get-Location) ('database\' + $nombre)
        if (!(Test-Path $archivo)) { continue }
        Get-Content -Raw -Encoding utf8 -LiteralPath $archivo | & $cliente @opciones | Out-Null
        if ($LASTEXITCODE) { throw "Error de instalación aislada al aplicar $nombre" }
    }
    "ALTER USER 'sigmud_app'@'127.0.0.1' IDENTIFIED BY '$testPass' ACCOUNT UNLOCK;" | & $cliente @opciones | Out-Null
    if ($LASTEXITCODE) { throw 'No se configuró la cuenta aislada de aplicación' }
    # Ventanillas sintéticas para probar las reservas de los dos perfiles de atención.
    @"
USE sigmud_db;
INSERT INTO ventanillas (area_id, codigo, nombre)
SELECT id, 'QA-WEB', CONCAT('Ventanilla QA ', nombre) FROM areas
WHERE codigo IN ('ATENCION_VECINO','DEFENSA_CIVIL');
INSERT INTO ventanillas_funcionarios (ventanilla_id, funcionario_id)
SELECT v.id, u.id FROM ventanillas v
JOIN usuarios u ON u.area_id = v.area_id
WHERE v.codigo = 'QA-WEB' AND u.nombre_usuario IN ('demo.defensa','demo.atencion');
"@ | & $cliente @opciones | Out-Null
    if ($LASTEXITCODE) { throw 'No se configuraron las ventanillas sintéticas de integración' }
    $classPath = 'target\test-classes;target\sigmud\WEB-INF\classes;target\sigmud\WEB-INF\lib\*'
    & "$env:JAVA_HOME\bin\java.exe" '-Dfile.encoding=UTF-8' -cp $classPath pe.gob.sigmud.service.ComprobarGestionMySql
    if ($LASTEXITCODE) { throw "Falló la prueba Java con código $LASTEXITCODE" }
    & "$PSScriptRoot\ComprobarWeb.ps1" -Tomcat $Tomcat -Directorio $qa -RevisionVisual:$RevisionVisual
} finally {
    if (Test-Path -LiteralPath $init) { Remove-Item -LiteralPath $init }
    if ($null -ne $proc) {
        $proc.Refresh()
        if (!$proc.HasExited) {
            & (Join-Path $mysql 'bin\mysqladmin.exe') '--no-defaults' '--protocol=TCP' '--host=127.0.0.1' "--port=$port" '--user=root' '--ssl-mode=REQUIRED' shutdown 2>$null
            if (!$proc.WaitForExit(8000)) { Stop-Process -Id $proc.Id }
        }
    }
    foreach ($variable in $variables) {
        if ($null -eq $entornoOriginal[$variable]) {
            Remove-Item -LiteralPath "Env:$variable" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($variable, $entornoOriginal[$variable], 'Process')
        }
    }
}
