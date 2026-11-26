param([string]$Tomcat, [string]$Directorio, [switch]$RevisionVisual)
$ErrorActionPreference = 'Stop'
$puerto = 18091
if (Get-NetTCPConnection -State Listen -LocalPort $puerto -ErrorAction SilentlyContinue) { throw 'Puerto web de pruebas ocupado.' }
if ($env:SIGMUD_DB_URL -ne 'jdbc:mysql://127.0.0.1:13306/sigmud_db' -or !$env:SIGMUD_QA_CLAVE) {
    throw 'Usa ProbarIntegracion.ps1 para preparar el entorno aislado.'
}

$libs = "$Tomcat\bin\tomcat-juli.jar;$Tomcat\lib\*"
$main = Join-Path $PSScriptRoot 'ServidorPruebas.java'
$war = (Resolve-Path -LiteralPath 'target\sigmud.war').Path
$base = Join-Path $Directorio 'tomcat'
$servidor = $null
$perfilLocal = Join-Path $Directorio 'perfil-local'
$directorioConfig = Join-Path $Directorio 'config'
$privado = Join-Path $directorioConfig 'sigmud-local.properties'
$web = "http://localhost:$puerto/sigmud"

function IniciarTomcatLocal {
    # Aislar también el entorno heredado del IDE/terminal que invoca la prueba.
    $nombres = 'SIGMUD_DB_URL','SIGMUD_DB_USER','SIGMUD_DB_PASSWORD','SIGMUD_QA_CLAVE','MYSQL_PWD'
    $guardado = @{}
    try {
        foreach ($nombre in $nombres) {
            $guardado[$nombre] = [Environment]::GetEnvironmentVariable($nombre)
            Remove-Item -LiteralPath "Env:$nombre" -ErrorAction SilentlyContinue
        }
        return Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList @(
            '-Dfile.encoding=UTF-8', ('"-Duser.home=' + $perfilLocal + '"'), '--class-path', ('"' + $libs + '"'),
            ('"' + $main + '"'), ('"' + $base + '"'), ('"' + $war + '"')
        ) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $Directorio 'tomcat.stdout') -RedirectStandardError (Join-Path $Directorio 'tomcat.stderr')
    } finally {
        foreach ($nombre in $guardado.Keys) {
            if ($null -ne $guardado[$nombre]) { [Environment]::SetEnvironmentVariable($nombre, $guardado[$nombre]) }
        }
    }
}

function EsperarTomcat($proceso) {
    for ($i = 0; $i -lt 40; $i++) {
        $proceso.Refresh()
        if ($proceso.HasExited) { throw "No arrancó Tomcat. Revisa $Directorio." }
        try {
            if ((Invoke-WebRequest "$web/login" -TimeoutSec 3).StatusCode -eq 200) { return }
        } catch { Start-Sleep -Seconds 1 }
    }
    throw "El WAR no respondió en $web"
}

try {
    $null = New-Item -ItemType Directory -Path $directorioConfig
    $cp = 'target\test-classes;target\sigmud\WEB-INF\classes;target\sigmud\WEB-INF\lib\*'
    & "$env:JAVA_HOME\bin\java.exe" "-Duser.home=$perfilLocal" -cp $cp pe.gob.sigmud.util.ConfigurarLocal $Directorio --desde-entorno
    if ($LASTEXITCODE -ne 0) { throw 'Falló el asistente de configuración aislado.' }
    $servidor = IniciarTomcatLocal
    EsperarTomcat $servidor
    function Confirmar($condicion, $mensaje) {
        if (!$condicion) { throw $mensaje }
        Write-Output "OK $mensaje"
    }
    function TokenCsrf($contenido) {
        $token = [regex]::Match($contenido, 'name="csrf"\s+value="([^"]+)"').Groups[1].Value
        if (!$token) { throw 'No se encontró el token de formulario.' }
        return $token
    }
    function Entrar($cuenta, $clave) {
        $sesion = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
        $formulario = Invoke-WebRequest "$web/login" -WebSession $sesion -TimeoutSec 10
        $resultado = Invoke-WebRequest "$web/login" -WebSession $sesion -Method Post -TimeoutSec 35 -SkipHttpErrorCheck -Body @{
            csrf = TokenCsrf $formulario.Content
            usuario = $cuenta
            contrasena = $clave
        }
        return [pscustomobject]@{ Sesion = $sesion; Pagina = $resultado }
    }
    $publico = Invoke-WebRequest "$web/inicio" -TimeoutSec 10
    Confirmar ($publico.BaseResponse.RequestMessage.RequestUri.AbsolutePath -eq '/sigmud/login') 'Protección sin sesión'
    $invalido = Entrar 'integracion.inexistente' ([guid]::NewGuid().ToString())
    Confirmar ($invalido.Pagina.Content.Contains('No pudimos iniciar sesión')) 'Rechazo de credenciales incorrectas'
    $admin = Entrar 'demo.admin' $env:SIGMUD_QA_CLAVE
    Confirmar ($admin.Pagina.StatusCode -eq 200 -and $admin.Pagina.Content.Contains('Bienvenido, Administración')) 'Login y dashboard autenticados'

    Stop-Process -Id $servidor.Id
    $servidor.WaitForExit()
    $servidor = IniciarTomcatLocal
    EsperarTomcat $servidor
    $admin = Entrar 'demo.admin' $env:SIGMUD_QA_CLAVE
    Confirmar ($admin.Pagina.Content.Contains('Bienvenido, Administración')) 'Login con archivo local tras reiniciar Tomcat sin variables JDBC'

    $perfiles = @(
        @('demo.admin', 'ADMINISTRADOR', 'Supervisión municipal', 'Administración de cuentas'),
        @('demo.mesa', 'MESA_PARTES', 'Mesa de Partes', 'Registro y derivación'),
        @('demo.rentas', 'RENTAS', 'Consulta tributaria', 'Vínculos prediales'),
        @('demo.defensa', 'FISCALIZACION_DEFENSA_CIVIL', 'Fiscalización y Defensa Civil', 'Cola de respuesta'),
        @('demo.atencion', 'ATENCION_VECINO', 'Atención al Vecino', 'Seguimiento de solicitudes'),
        @('demo.mercados', 'MERCADOS', 'Control de mercados', 'Disponibilidad por sector'),
        @('demo.almacen', 'ALMACEN', 'Inventario y logística', 'Materiales con stock bajo'),
        @('demo.registro', 'REGISTRO_CIVIL', 'Consulta de personas', 'Fichas de personas')
    )
    $sesiones = @{ 'demo.admin' = $admin }
    foreach ($perfil in $perfiles) {
        if (!$sesiones.ContainsKey($perfil[0])) { $sesiones[$perfil[0]] = Entrar $perfil[0] $env:SIGMUD_QA_CLAVE }
        $vista = $sesiones[$perfil[0]].Pagina
        Confirmar ($vista.StatusCode -eq 200 -and $vista.Content.Contains(('data-perfil="{0}"' -f $perfil[1])) -and
            $vista.Content.Contains(('<h1 id="dashboard-titulo">{0}</h1>' -f $perfil[2])) -and
            $vista.Content.Contains($perfil[3]) -and $vista.Content.Contains('class="dashboard-number"')) "Panel MySQL $($perfil[1])"
    }
    $rentas = $sesiones['demo.rentas']
    $civil = $sesiones['demo.registro']
    Confirmar ($rentas.Pagina.Content.Contains('name="tipo" type="hidden" value="PREDIOS"') -and
        !$rentas.Pagina.Content.Contains('Fichas de personas') -and
        $civil.Pagina.Content.Contains('name="tipo" type="hidden" value="NATURAL"') -and
        !$civil.Pagina.Content.Contains('Vínculos prediales')) 'Consultas diferenciadas de Rentas y Registro Civil'

    foreach ($recurso in @('/usuarios','/usuarios?permisos=1','/usuarios?nueva=1','/contribuyentes','/contribuyentes?nuevo=1',
            '/tramites','/tramites?nuevo=1','/reclamos','/reclamos?nuevo=1','/citas','/citas?nuevo=1','/citas?disponibilidad=1',
            '/mercados','/mercados?catalogo=SECTORES','/mercados?nuevo=1','/almacen','/almacen?nuevo=1','/reportes','/busquedas?q=INT')) {
        $res = Invoke-WebRequest "$web$recurso" -WebSession $admin.Sesion -TimeoutSec 30 -SkipHttpErrorCheck
        Confirmar ($res.StatusCode -eq 200 -and $res.BaseResponse.RequestMessage.RequestUri.AbsolutePath -ne '/sigmud/login') "Vista Tomcat $recurso (HTTP $($res.StatusCode))"
    }
    $csv = Invoke-WebRequest "$web/reportes?reporte=STOCK&csv=1" -WebSession $admin.Sesion -TimeoutSec 10
    Confirmar ($csv.Headers['Content-Type'] -match 'text/csv' -and $csv.Content.Contains('Insumo actualizado')) 'Descarga CSV con datos de MySQL'
    $bloqueado = Invoke-WebRequest "$web/almacen" -WebSession $rentas.Sesion -TimeoutSec 10 -SkipHttpErrorCheck
    $exportacion = Invoke-WebRequest "$web/reportes?reporte=STOCK&csv=1" -WebSession $rentas.Sesion -TimeoutSec 10 -SkipHttpErrorCheck
    Confirmar ($bloqueado.StatusCode -eq 403 -and $exportacion.StatusCode -eq 403) 'Autorización de ruta y exportación'
    $sinCsrf = Invoke-WebRequest "$web/salir" -WebSession $admin.Sesion -Method Post -TimeoutSec 10 -SkipHttpErrorCheck
    Confirmar ($sinCsrf.StatusCode -eq 403) 'Protección CSRF'
    & "$PSScriptRoot\ComprobarFlujosWeb.ps1" -Web $web -Sesiones $sesiones

    if ($RevisionVisual) {
        $marca = Join-Path (Get-Location) 'target\revision-integral-completa'
        if (Test-Path -LiteralPath $marca) { Remove-Item -LiteralPath $marca }
        Write-Output "REVISION_VISUAL $web"
        for ($i = 0; $i -lt 120 -and !(Test-Path -LiteralPath $marca); $i++) { Start-Sleep -Seconds 2 }
    }
} finally {
    if (Test-Path -LiteralPath $privado) { Remove-Item -LiteralPath $privado }
    if ($null -ne $servidor) {
        $servidor.Refresh()
        if (!$servidor.HasExited) { Stop-Process -Id $servidor.Id }
    }
}
