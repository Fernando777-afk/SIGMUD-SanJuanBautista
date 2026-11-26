param([string]$Web, [hashtable]$Sesiones)
$ErrorActionPreference = 'Stop'
if ($Web -ne 'http://localhost:18091/sigmud' -or $env:SIGMUD_DB_URL -ne 'jdbc:mysql://127.0.0.1:13306/sigmud_db') {
    throw 'Esta prueba solo puede ejecutarse en la instancia aislada.'
}

function Leer($cuenta, $ruta) {
    Invoke-WebRequest "$Web$ruta" -WebSession $Sesiones[$cuenta].Sesion -TimeoutSec 25 -SkipHttpErrorCheck
}
function Enviar($cuenta, $ruta, [hashtable]$datos) {
    $datos.csrf = TokenCsrf $Sesiones[$cuenta].Pagina.Content
    Invoke-WebRequest "$Web$ruta" -WebSession $Sesiones[$cuenta].Sesion -Method Post -Body $datos -TimeoutSec 25 -SkipHttpErrorCheck
}
function VerTexto($respuesta, $texto, $mensaje) {
    Confirmar ($respuesta.StatusCode -eq 200 -and $respuesta.Content.Contains($texto)) $mensaje
}
function IdDestino($respuesta) {
    $coincidencia = [regex]::Match($respuesta.BaseResponse.RequestMessage.RequestUri.Query, '[?&]id=(\d+)')
    if (!$coincidencia.Success) { throw "No se encontró el ID en la ficha tras guardar. HTTP $($respuesta.StatusCode)" }
    return $coincidencia.Groups[1].Value
}
function ValorCampo($html, $campo) {
    $valor = [regex]::Match($html, ('name="{0}"\s+value="([^"]+)"' -f [regex]::Escape($campo)))
    if (!$valor.Success) { throw "No se encontró el campo $campo." }
    return $valor.Groups[1].Value
}
function Opciones($html, $campo) {
    $seleccion = [regex]::Match($html, ('(?s)<select\b[^>]*\bname="{0}"[^>]*>(.*?)</select>' -f [regex]::Escape($campo)))
    if (!$seleccion.Success) { throw "No se encontró la selección $campo." }
    foreach ($opcion in [regex]::Matches($seleccion.Groups[1].Value, '<option value="(\d+)"[^>]*>([^<]+)</option>')) {
        [pscustomobject]@{ Id = $opcion.Groups[1].Value; Etiqueta = [Net.WebUtility]::HtmlDecode($opcion.Groups[2].Value) }
    }
}

# Autorización de los nueve módulos para las ocho cuentas existentes de QA.
$todos = @('/usuarios','/contribuyentes','/tramites','/reclamos','/citas','/mercados','/almacen','/reportes','/busquedas')
$permitidos = @{
    'demo.admin' = $todos
    'demo.mesa' = @('/contribuyentes','/tramites','/reportes','/busquedas')
    'demo.rentas' = @('/contribuyentes','/tramites','/citas','/reportes','/busquedas')
    'demo.defensa' = @('/contribuyentes','/tramites','/reclamos','/citas','/reportes','/busquedas')
    'demo.atencion' = @('/contribuyentes','/reclamos','/citas','/reportes','/busquedas')
    'demo.mercados' = @('/contribuyentes','/mercados','/reportes','/busquedas')
    'demo.almacen' = @('/almacen','/reportes','/busquedas')
    'demo.registro' = @('/contribuyentes','/tramites','/citas','/reportes','/busquedas')
}
foreach ($cuenta in $permitidos.Keys | Sort-Object) {
    foreach ($modulo in $todos) {
        $esperado = if ($modulo -in $permitidos[$cuenta]) { 200 } else { 403 }
        $respuesta = Leer $cuenta $modulo
        if ($respuesta.StatusCode -ne $esperado) {
            throw "$cuenta $modulo devolvió HTTP $($respuesta.StatusCode). Se esperaba HTTP $esperado."
        }
    }
    Write-Output "OK Matriz de los nueve módulos para $cuenta"
}

$admin = 'demo.admin'
$mesa = 'demo.mesa'
$rentas = 'demo.rentas'
$civil = 'demo.registro'
$defensa = 'demo.defensa'
$atencion = 'demo.atencion'
$mercados = 'demo.mercados'
$almacen = 'demo.almacen'

VerTexto (Leer $rentas '/contribuyentes?tipo=JURIDICA') 'Entidad ficticia de prueba B' 'Rentas consulta personas jurídicas'
$fichas = Leer $civil '/contribuyentes'
Confirmar ($fichas.Content.Contains('Fichas de personas') -and $fichas.Content.Contains('Persona ficticia de prueba A') -and
    !$fichas.Content.Contains('Entidad ficticia de prueba B')) 'Registro Civil abre fichas naturales'
VerTexto (Leer $civil '/contribuyentes?tipo=') 'Entidad ficticia de prueba B' 'Registro Civil conserva el acceso autorizado al padrón general'
Confirmar ($fichas.Content.Contains('Nombre, DNI o RUC') -and $fichas.Content.Contains('Abrir consulta general') -and
    !$fichas.Content.Contains('Padrón completo') -and !$fichas.Content.Contains('Nombre, documento o código de predio') -and
    !$fichas.Content.Contains('<select class="form-select" id="consulta-tipo"')) 'Fichas personales tienen un filtro propio y un acceso secundario a la consulta general'
$porPredioEnFichas = Leer $civil '/contribuyentes?tipo=NATURAL&q=DEMO-PREDIO-01'
Confirmar ($porPredioEnFichas.Content.Contains('No se encontraron personas naturales') -and
    !$porPredioEnFichas.Content.Contains('Persona ficticia de prueba A')) 'El filtro personal no devuelve coincidencias por predio'
VerTexto (Leer $civil '/contribuyentes?tipo=&q=DEMO-PREDIO-01') 'Persona ficticia de prueba A' 'Registro Civil conserva la búsqueda por predio en la consulta general'
VerTexto (Leer $rentas '/contribuyentes?q=DEMO-PREDIO-01') 'Persona ficticia de prueba A' 'Padrón de Rentas conserva la búsqueda predial'
$prediosConAreaAnterior = Leer $rentas '/busquedas?tipo=PREDIOS&area=99999&q=DEMO-PREDIO-01'
Confirmar ($prediosConAreaAnterior.Content.Contains('Código de predio') -and
    $prediosConAreaAnterior.Content.Contains('Persona ficticia de prueba A') -and
    !$prediosConAreaAnterior.Content.Contains('<select class="form-select" id="consulta-area"')) 'Búsqueda predial ignora filtros ajenos al criterio elegido'
VerTexto (Leer $mesa '/tramites?nuevo=1') 'Solicitante con ficha vigente' 'Formulario de expedientes identifica al solicitante'
VerTexto (Leer $defensa '/reclamos?estado=ABIERTOS&prioridad=URGENTE') 'DEMO-CASO-002' 'Atajo de Defensa Civil consulta la cola urgente del área'
VerTexto (Leer $atencion '/reclamos?nuevo=1') 'Datos de la solicitud' 'Atención al Vecino abre su formulario de solicitud'
VerTexto (Leer $mercados '/mercados?catalogo=SECTORES') 'Nombre del sector' 'El catálogo de Mercados identifica sus sectores'
VerTexto (Leer $almacen '/reportes?reporte=MOVIMIENTOS') 'Tipo de movimiento' 'El reporte de Almacén filtra por tipo de movimiento'
VerTexto (Leer $admin '/usuarios') 'Nombre visible o nombre de usuario' 'Administración identifica los criterios de búsqueda de cuentas'

$expedienteRentas = Leer $rentas '/tramites?estado=ATENDIDO'
$expedienteCivil = Leer $civil '/tramites?q=DEMO-EXP-001'
Confirmar ($expedienteRentas.Content.Contains('DEMO-EXP-001') -and !$expedienteCivil.Content.Contains('Solicitud de prueba A') -and
    $expedienteCivil.Content.Contains('No hay expedientes en tu ámbito')) 'Expedientes y estados vacíos respetan el área'
VerTexto (Leer $rentas '/busquedas?tipo=PREDIOS&q=DEMO-PREDIO-01') 'Predio asociado' 'Búsqueda de Rentas por código predial'
VerTexto (Leer $civil '/contribuyentes?tipo=NATURAL&q=00000000') 'Persona ficticia de prueba A' 'Búsqueda personal por DNI'
Confirmar ((Leer $civil '/busquedas?tipo=MATERIALES&q=Insumo').StatusCode -eq 403) 'Búsqueda restringida por permiso'
Confirmar ((Leer $rentas '/contribuyentes?nuevo=1').StatusCode -eq 403) 'Formulario de alta restringido para Rentas'
VerTexto (Leer $civil '/citas?semana=2030-06-15&dia=2030-06-15&estado=PROGRAMADA') 'DEMO-CITA-004' 'Calendario de Registro Civil filtra por día'
Confirmar (!(Leer $civil '/citas?semana=2030-06-15&dia=2030-06-14&estado=PROGRAMADA').Content.Contains('DEMO-CITA-004')) 'Calendario excluye otros días'
VerTexto (Leer $defensa '/reclamos?estado=ABIERTOS&prioridad=URGENTE') 'DEMO-CASO-002' 'Cola de alta o crítica muestra la emergencia del área'
VerTexto (Leer $civil '/busquedas?tipo=DOCUMENTOS&q=00000000') 'Documento de contribuyente' 'Búsqueda transversal por documento'
VerTexto (Leer $civil '/busquedas?tipo=CITAS&q=DEMO-CITA-004') 'DEMO-CITA-004' 'Búsqueda transversal de citas'
VerTexto (Leer $defensa '/busquedas?tipo=RECLAMOS&q=DEMO-CASO-002') 'DEMO-CASO-002' 'Búsqueda transversal de emergencias'

$altaUsuario = Leer $admin '/usuarios?nueva=1'
$rol = (Opciones $altaUsuario.Content 'rol' | Where-Object Etiqueta -eq 'Rentas').Id
$areaRentas = (Opciones $altaUsuario.Content 'area' | Where-Object Etiqueta -eq 'Rentas').Id
$cuentaTemporal = 'qa.auditoria.web'
$baseUsuario = @{ id='0'; usuario=$cuentaTemporal; nombreVisible='Cuenta temporal QA'; rol=$rol; area=$areaRentas;
    activa='1'; contrasena=$env:SIGMUD_QA_CLAVE }
$registroUsuario = Enviar $admin '/usuarios' $baseUsuario
VerTexto $registroUsuario 'Cuenta temporal QA' 'Alta de usuario por el formulario web'
$paginaUsuario = Leer $admin "/usuarios?q=$cuentaTemporal&estado=activa"
$idUsuario = [regex]::Match($paginaUsuario.Content, '\?editar=(\d+)').Groups[1].Value
if (!$idUsuario) { throw 'No se encontró la nueva cuenta en el listado filtrado.' }
$edicion = $baseUsuario.Clone()
$edicion.id = $idUsuario
$edicion.nombreVisible = 'Cuenta renombrada QA'
$edicion.activa = ''
$edicion.contrasena = ''
VerTexto (Enviar $admin '/usuarios' $edicion) 'Cuenta renombrada QA' 'Edición y desactivación de cuenta temporal'
VerTexto (Entrar $cuentaTemporal $env:SIGMUD_QA_CLAVE).Pagina 'No pudimos iniciar sesión' 'La cuenta desactivada queda fuera del login'
VerTexto (Leer $admin '/usuarios?permisos=1') 'Administrar usuarios, roles y permisos' 'Consulta de permisos del administrador'
$edicion.activa = '1'
$nuevaClaveTemporal = [guid]::NewGuid().ToString()
$edicion.contrasena = $nuevaClaveTemporal
VerTexto (Enviar $admin '/usuarios' $edicion) 'Cuenta renombrada QA' 'Restablecimiento de contraseña y reactivación de cuenta temporal'
$reentrada = Entrar $cuentaTemporal $nuevaClaveTemporal
VerTexto $reentrada.Pagina 'Consulta tributaria' 'Nueva contraseña autentica con el rol y área asignados'

$sinVentanilla = $edicion.Clone()
$sinVentanilla.area = ''
$sinVentanilla.contrasena = ''
VerTexto (Enviar $admin '/usuarios' $sinVentanilla) 'Cuenta renombrada QA' 'La cuenta temporal cambia a un ámbito sin ventanillas'
$agendaSinVentanilla = Invoke-WebRequest "$Web/citas" -WebSession $reentrada.Sesion -TimeoutSec 25
Confirmar ($agendaSinVentanilla.Content.Contains('Para agendar se necesita una ventanilla') -and
    !$agendaSinVentanilla.Content.Contains('Programar cita') -and
    !$agendaSinVentanilla.Content.Contains('Comprobar disponibilidad')) 'Agenda conserva consulta cuando no puede programar'
$altaSinVentanilla = Invoke-WebRequest "$Web/citas?nuevo=1" -WebSession $reentrada.Sesion -TimeoutSec 25
VerTexto $altaSinVentanilla 'No hay ventanillas habilitadas en tu ámbito' 'El formulario sin ventanillas explica la configuración pendiente'
$null = Invoke-WebRequest "$Web/salir" -WebSession $reentrada.Sesion -Method Post -TimeoutSec 10 -Body @{csrf=(TokenCsrf $reentrada.Pagina.Content)}

$persona = @{
    op='guardar'; tipo='NATURAL'; dni='98123077'; nombre='Persona sintética QA web'; nacimiento='1990-02-17'; telefono='900123077'
}
$invalida = $persona.Clone()
$invalida.dni = '12'
$rechazada = Enviar $mesa '/contribuyentes' $invalida
Confirmar ($rechazada.Content.Contains('El DNI debe contener ocho dígitos') -and
    $rechazada.Content.Contains('Persona sintética QA web')) 'Validación conserva la entrada en el formulario'
$altaPersona = Enviar $mesa '/contribuyentes' $persona
$idPersona = IdDestino $altaPersona
VerTexto $altaPersona 'Persona sintética QA web' 'Mesa de Partes registra y consulta una persona'
$persona.id = $idPersona
$persona.nombre = 'Persona sintética editada QA'
VerTexto (Enviar $mesa '/contribuyentes' $persona) 'Persona sintética editada QA' 'Edición persistente de una ficha'
VerTexto (Enviar $mesa '/contribuyentes' @{op='predio';id=$idPersona;predio='QA-PREDIO-WEB'}) 'QA-PREDIO-WEB' 'Vínculo predial persistente'
$empresa = Enviar $mesa '/contribuyentes' @{op='guardar';tipo='JURIDICA';ruc='99001230127';nombre='Empresa sintética QA web'}
VerTexto (Enviar $mesa '/contribuyentes' @{op='baja';id=(IdDestino $empresa)}) 'Con baja' 'Alta jurídica y baja lógica desde la ficha'

$formTramite = Leer $mesa "/tramites?nuevo=1&contribuyente=$idPersona"
$areaMesa = (Opciones $formTramite.Content 'area' | Where-Object Etiqueta -eq 'Mesa de Partes').Id
$tipoTramite = 'Expediente sintético QA web'
$altaTramite = Enviar $mesa '/tramites' @{op='registrar';contribuyente=$idPersona;area=$areaMesa;tipo=$tipoTramite;descripcion='QA de expediente'}
$codigoTramite = [regex]::Match($altaTramite.Content, 'Expediente registrado: (EXP-[A-Z0-9-]+)').Groups[1].Value
if (!$codigoTramite) { throw 'El alta web de expediente no devolvió un código.' }
$tramites = Leer $mesa "/tramites?q=$codigoTramite"
$idTramite = [regex]::Match($tramites.Content, '/tramites\?id=(\d+)').Groups[1].Value
if (!$idTramite) { throw 'No se encontró el expediente creado en la bandeja.' }
$fichaTramite = Leer $mesa "/tramites?id=$idTramite"
$version = ValorCampo $fichaTramite.Content 'version'
VerTexto (Enviar $mesa '/tramites' @{op='actualizar';id=$idTramite;version=$version;area=$areaRentas;
        estado='EN_TRAMITE';observacion='Derivación sintética por web'}) 'Se agregó el movimiento' 'Derivación de expediente por Mesa de Partes'
$txt = Leer $rentas "/tramites?id=$idTramite&exportar=1"
Confirmar ($txt.Headers['Content-Type'] -match 'text/plain' -and $txt.Content.Contains('Derivación sintética por web')) 'Rentas consulta y descarga el historial derivado'
Confirmar ((Leer $civil "/tramites?id=$idTramite").StatusCode -in @(400,403)) 'Registro Civil no abre un expediente de Rentas'

foreach ($perfilCaso in @(@($atencion, 'Atención al Vecino', 'RECLAMO'), @($defensa, 'Defensa Civil', 'EMERGENCIA'))) {
    $cuenta = $perfilCaso[0]
    $form = Leer $cuenta '/reclamos?nuevo=1'
    $areaCaso = (Opciones $form.Content 'area' | Where-Object Etiqueta -eq $perfilCaso[1]).Id
    $alta = Enviar $cuenta '/reclamos' @{op='registrar';area=$areaCaso;tipo=$perfilCaso[2];prioridad='ALTA';descripcion='Caso sintético QA web'}
    $idCaso = IdDestino $alta
    $opuesta = if ($cuenta -eq $atencion) { $defensa } else { $atencion }
    Confirmar ((Leer $opuesta "/reclamos?id=$idCaso").StatusCode -in @(400,403)) "Caso privado del área de $cuenta"
    VerTexto (Enviar $cuenta '/reclamos' @{op='atender';id=$idCaso;area=$areaCaso;areaAnterior=$areaCaso;
        estadoAnterior='PENDIENTE';prioridadAnterior='ALTA'; estado='ATENDIDO';prioridad='ALTA'
    }) 'Se guardó la atención' "Registro y atención de caso desde $cuenta"
    VerTexto (Leer $cuenta '/reclamos?estado=ATENDIDO&prioridad=ALTA') 'Atendido' "Filtro de casos atendidos de $cuenta"
}

foreach ($cuenta in @($rentas, $civil, $atencion, $defensa)) {
    $programar = Leer $cuenta '/citas?nuevo=1'
    $ventanilla = Opciones $programar.Content 'v' | Select-Object -First 1
    if (!$ventanilla) { throw "No se encontraron ventanillas para $cuenta." }
    $IdVentanilla = $ventanilla.Id
    $form = Leer $cuenta "/citas?nuevo=1&v=$IdVentanilla"
    $personaDisponible = Opciones $form.Content 'contribuyente' | Where-Object Etiqueta -like 'Persona sintética editada QA*' | Select-Object -First 1
    $funcionario = Opciones $form.Content 'funcionario' | Select-Object -First 1
    if (!$personaDisponible -or !$funcionario) { throw "Faltan opciones de cita para $cuenta." }
    $inicio = [DateTime]::Today.AddDays(7).AddHours(10)
    $desde = $inicio.ToString('yyyy-MM-ddTHH:mm')
    $hasta = $inicio.AddMinutes(20).ToString('yyyy-MM-ddTHH:mm')
    $consulta = "/citas?disponibilidad=1&ventanilla=$IdVentanilla&funcionario=$($funcionario.Id)&inicio=$desde&fin=$hasta"
    VerTexto (Leer $cuenta $consulta) 'El horario está disponible' "Disponibilidad real de cita para $cuenta"
    $reserva = @{op='guardar';contribuyente=$idPersona;ventanilla=$IdVentanilla;funcionario=$funcionario.Id;
        inicio=$desde;fin=$hasta;motivo='Reserva sintética QA web'}
    $creada = Enviar $cuenta '/citas' $reserva
    VerTexto $creada 'Se programó la cita' "Alta persistente de cita de $cuenta"
    VerTexto (Leer $cuenta $consulta) 'coincide con una reserva' "Disponibilidad detecta la reserva de $cuenta"
    $idCita = IdDestino $creada
    $opuesta = if ($cuenta -eq $rentas) { $civil } else { $rentas }
    Confirmar ((Leer $opuesta "/citas?id=$idCita").StatusCode -in @(400,403)) "Cita limitada al área de $cuenta"
    $reprogramar = Leer $cuenta "/citas?reprogramar=1&id=$idCita&v=$IdVentanilla"
    VerTexto $reprogramar 'Reprogramar cita' "Formulario de reemplazo de $cuenta"
    $reserva.id = $idCita
    $reserva.inicio = $inicio.AddHours(1).ToString('yyyy-MM-ddTHH:mm')
    $reserva.fin = $inicio.AddHours(1).AddMinutes(20).ToString('yyyy-MM-ddTHH:mm')
    $reemplazo = Enviar $cuenta '/citas' $reserva
    VerTexto $reemplazo 'Se reprogramó la cita' "Reprogramación de $cuenta"
    VerTexto (Leer $cuenta "/citas?id=$idCita") 'Cancelada' "La cita anterior de $cuenta queda cancelada"
    VerTexto (Enviar $cuenta '/citas' @{op='finalizar';id=(IdDestino $reemplazo);estado='CANCELADA'}) 'Cancelada' "Cancelación persistente de $cuenta"
}
VerTexto (Leer $rentas '/citas?estado=CANCELADA') 'DEMO-CITA-001' 'Agenda de Rentas permite consultar su historial'

VerTexto (Enviar $mercados '/mercados' @{op='catalogo';catalogo='MERCADOS';codigo='QA-MERCADO-WEB';nombre='Mercado sintético QA web'}) 'Mercado sintético QA web' 'Alta de mercado'
VerTexto (Enviar $mercados '/mercados' @{op='catalogo';catalogo='SECTORES';codigo='QA-SECTOR-WEB';nombre='Sector sintético QA web'}) 'Sector sintético QA web' 'Alta de sector'
$formPuesto = Leer $mercados '/mercados?nuevo=1'
$idMercado = (Opciones $formPuesto.Content 'mercado' | Where-Object Etiqueta -eq 'Mercado sintético QA web').Id
$idSector = (Opciones $formPuesto.Content 'sector' | Where-Object Etiqueta -eq 'Sector sintético QA web').Id
VerTexto (Enviar $mercados '/mercados' @{op='catalogo';catalogo='SECTORES';editar=$idSector;nombre='Sector editado QA web'}) 'Sector editado QA web' 'Edición de sector'
$puesto = Enviar $mercados '/mercados' @{op='alta';numero='QA-PUESTO-WEB';mercado=$idMercado;sector=$idSector}
$idPuesto = IdDestino $puesto
VerTexto $puesto 'Se registró el puesto' 'Alta de puesto'
$asignado = Enviar $mercados '/mercados' @{op='asignar';id=$idPuesto;contribuyente=$idPersona;giro='Giro sintético QA web'}
VerTexto $asignado 'Se asignó el puesto' 'Asignación de puesto persistente'
$idAsignacion = ValorCampo $asignado.Content 'asignacion'
VerTexto (Enviar $mercados '/mercados' @{op='liberar';id=$idPuesto;asignacion=$idAsignacion}) 'Se liberó el puesto' 'Liberación de puesto'
VerTexto (Leer $mercados "/mercados?id=$idPuesto") 'Giro sintético QA web' 'Historial del puesto liberado'
VerTexto (Leer $mercados '/busquedas?tipo=PUESTOS&q=QA-PUESTO-WEB') 'Mercado sintético QA web' 'Búsqueda y enlace de puestos'

$material = Enviar $almacen '/almacen' @{op='material';codigo='QA-MATERIAL-WEB';nombre='Insumo sintético QA web';unidad='unidades';minimo='4'}
$idMaterial = IdDestino $material
VerTexto $material 'Se guardó el material' 'Alta de material'
VerTexto (Enviar $almacen '/almacen' @{op='material';id=$idMaterial;nombre='Insumo editado QA web';minimo='4'}) 'Insumo editado QA web' 'Edición de material'
VerTexto (Enviar $almacen '/almacen' @{op='lote';id=$idMaterial;codigo='QA-LOTE-WEB';vence=[DateTime]::Today.AddDays(15).ToString('yyyy-MM-dd')}) 'QA-LOTE-WEB' 'Alta de lote'
$movimiento = Leer $almacen "/almacen?id=$idMaterial&movimiento=1"
$lote = (Opciones $movimiento.Content 'loteId' | Where-Object Etiqueta -like 'QA-LOTE-WEB*').Id
$stock = @{op='movimiento';id=$idMaterial;loteId=$lote;tipo='INGRESO';cantidad='8';motivo='Ingreso sintético QA web'}
VerTexto (Enviar $almacen '/almacen' $stock) 'Ingreso sintético QA web' 'Ingreso de 8 unidades'
$stock.tipo = 'SALIDA'
$stock.area = $areaRentas
$stock.cantidad = '9'
$stock.motivo = 'Salida sintética QA web'
VerTexto (Enviar $almacen '/almacen' $stock) 'El saldo del lote no admite ese movimiento' 'Se impide la salida superior al saldo'
$stock.cantidad = '5'
VerTexto (Enviar $almacen '/almacen' $stock) '3.000 unidades' 'Salida de 5 unidades y saldo persistente de 3'
VerTexto (Leer $almacen '/almacen?alerta=VENCIMIENTOS&q=QA-MATERIAL-WEB') 'QA-MATERIAL-WEB' 'Filtro de vencimientos con saldo'
VerTexto (Leer $almacen '/busquedas?tipo=MATERIALES&q=QA-MATERIAL-WEB') 'Insumo editado QA web' 'Búsqueda de material actualizado'

$reportes = @('CONTRIBUYENTES','TRAMITES_ESTADO','TRAMITES_AREA','RECLAMOS','CITAS','OCUPACION','STOCK','MOVIMIENTOS')
foreach ($reporte in $reportes) {
    $salida = Leer $admin "/reportes?reporte=$reporte&csv=1"
    Confirmar ($salida.StatusCode -eq 200 -and $salida.Headers['Content-Type'] -match 'text/csv' -and
        $salida.Content.Split([char]10).Length -gt 2) "CSV web con encabezados y registros de $reporte"
}
VerTexto (Leer $civil '/reportes') 'Citas por estado y período' 'Reporte inicial pertinente de Registro Civil'
VerTexto (Leer $almacen '/reportes?reporte=STOCK') 'Insumo editado QA web' 'Reporte de stock incluye el nuevo saldo bajo'
VerTexto (Leer $rentas "/busquedas?tipo=EXPEDIENTES&q=$codigoTramite") $tipoTramite 'Búsqueda de expediente con alcance'
Confirmar (!(Leer $civil "/busquedas?tipo=EXPEDIENTES&q=$codigoTramite").Content.Contains($tipoTramite)) 'Búsqueda impide ver expedientes de otra área'

foreach ($cuenta in $permitidos.Keys) {
    $token = TokenCsrf $Sesiones[$cuenta].Pagina.Content
    $null = Invoke-WebRequest "$Web/salir" -WebSession $Sesiones[$cuenta].Sesion -Method Post -TimeoutSec 10 -Body @{csrf=$token}
    Confirmar ((Leer $cuenta '/inicio').BaseResponse.RequestMessage.RequestUri.AbsolutePath -eq '/sigmud/login') "Cierre de sesión para $cuenta"
}
