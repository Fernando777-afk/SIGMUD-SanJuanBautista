# Construye el ZIP desde entradas distribuibles, sin tomar configuración local ni artefactos generados.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$raiz = (Get-Item -LiteralPath (Join-Path $PSScriptRoot '..')).FullName
$destino = Join-Path $raiz 'distribucion'
$null = [IO.Directory]::CreateDirectory($destino)
$archivos = @('pom.xml','nb-configuration.xml','.gitignore','Configurar-SIGMUD.cmd','README.md',
    'REQUISITOS.md','DISENO.md','GUIA_OPERATIVA.md','config/sigmud-local.example.properties','database/README.md',
    'scripts/EmpaquetarDemo.ps1','scripts/ComprobarWeb.ps1','scripts/ComprobarFlujosWeb.ps1',
    'scripts/ProbarIntegracion.ps1','scripts/ServidorPruebas.java')
$archivos += Get-ChildItem -LiteralPath (Join-Path $raiz 'database') -File |
    Where-Object Name -Match '^0[1-8]_[a-z_]+\.sql$' | ForEach-Object { 'database/' + $_.Name }
$extensiones = '.java','.jsp','.jspf','.css','.js','.jpg','.svg','.xml'
$archivos += Get-ChildItem -LiteralPath (Join-Path $raiz 'src') -File -Recurse |
    Where-Object { $_.Extension -in $extensiones } |
    ForEach-Object { $_.FullName.Substring($raiz.Length + 1).Replace('\','/') }
$temporal = Join-Path $destino ('sigmud-' + [guid]::NewGuid().ToString('N') + '.tmp')
try {
    $zip = [IO.Compression.ZipFile]::Open($temporal,[IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($nombre in $archivos) {
            $null = [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $raiz $nombre),
                "SIGMUD/$nombre",[IO.Compression.CompressionLevel]::Optimal)
        }
    } finally { $zip.Dispose() }
    Move-Item -LiteralPath $temporal -Destination (Join-Path $destino 'SIGMUD-DEMO.zip') -Force
} finally {
    if (Test-Path -LiteralPath $temporal) { Remove-Item -LiteralPath $temporal }
}
Write-Output (Join-Path $destino 'SIGMUD-DEMO.zip')
