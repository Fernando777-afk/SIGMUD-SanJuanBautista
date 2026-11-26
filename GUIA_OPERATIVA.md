# Guía operativa de SIGMUD

SIGMUD es una aplicación interna para demostración académica. Las cuentas y los registros de `03_datos_demo.sql` son ficticios. Sus códigos y fechas se conservan para que sea posible repetir la demostración.

## Abrir el sistema

En NetBeans abre `C:\Proyectos\SIGMUD` como proyecto Maven. Verifica Java 17, selecciona Tomcat 10.1 y configura el contexto `/sigmud`. En la primera instalación prepara MySQL mediante `database/README.md`, compila y abre `Configurar-SIGMUD.cmd`. Introduce la clave JDBC una sola vez. El asistente registra un archivo privado del proyecto bajo tu perfil. Las cuentas web nuevas se habilitan por separado con `Configurar-SIGMUD.cmd cuentas`. Para el uso diario abre NetBeans desde su icono y ejecuta **Run Project (F6)** y abre [SIGMUD](http://localhost:8080/sigmud/).

Para una instalación existente, revisa qué actualizaciones entre 06 y 08 faltan y aplica únicamente las pendientes desde Workbench antes de probar altas de citas o sectores. Los pasos están en [la guía de base de datos](database/README.md). En el formulario de entrada usa el identificador existente y su clave, por ejemplo `demo.admin`. El nombre institucional de presentación es Administración.

Si falla la conexión, abre CMD en la raíz y ejecuta `Configurar-SIGMUD.cmd comprobar`. Revisa MySQL, el perfil de Windows y la ubicación privada. El [README](README.md) detalla el diagnóstico, la precedencia del entorno y cómo instalar desde el ZIP en otra computadora.

## Recorrido de demostración

| Módulo | Recorrido |
| --- | --- |
| Usuarios y acceso | Filtra cuentas por rol o estado. Abre los permisos y después edita el área o el nombre visible de una cuenta. Los identificadores reservados se muestran en la ficha. El rol y la habilitación de la cuenta administradora en uso están protegidos. |
| Contribuyentes y vecinos | Registra una persona natural con DNI de ocho dígitos o RUC de once. Una persona jurídica requiere RUC y no admite DNI ni fecha de nacimiento. Vincula un predio desde la ficha y búscalo por su código. La baja conserva los vínculos y el historial. |
| Trámite documentario | Desde la ficha del contribuyente o el listado, registra un expediente para un área vigente. Busca el código generado. Desde Seguimiento agrega un movimiento o descarga el historial TXT. La derivación limita las consultas posteriores al área de destino. |
| Reclamos y emergencias | Registra un caso con tipo, prioridad y área responsable. La cola abierta se actualiza cada 30 segundos mientras está visible. En el detalle cambia la prioridad, el área dentro de tu alcance o el estado de atención. |
| Citas municipales | Abre el calendario semanal o la lista por estado. Para programar, aplica la selección de ventanilla y elige un funcionario habilitado. Indica contribuyente e intervalo y comprueba disponibilidad. En Reprogramar se cancela la reserva anterior y se crea la nueva en una transacción. Los registros cancelados siguen consultables por estado. |
| Mercados y puestos | Crea mercado y sector. Registra un puesto y asígnalo a un contribuyente vigente. Al liberarlo, verifica que se actualice la ocupación y permanezca la asignación en el historial. |
| Almacén y logística | Registra material y lote. Ingresa existencias y registra una salida con área solicitante. Revisa historial, stock mínimo y vencimientos. El saldo solo cambia a través de movimientos. |
| Reportes de gestión | Elige un reporte y aplica los filtros que correspondan. Usa Exportar CSV para todo el resultado filtrado o Imprimir página para las filas visibles. El reporte de trámites atendidos cuenta expedientes distintos por área y período. |
| Búsquedas y clasificación | Escribe un mínimo de dos caracteres. Filtra por categoría o área cuando corresponda, cambia el orden y abre la ficha de un resultado. |

## Prueba reproducible

Desde la raíz del proyecto recompila con `mvn verify`. En PowerShell 7 ejecuta `./scripts/ProbarIntegracion.ps1`. Usa `-MySql`, `-Tomcat` y `-Jdk` si las rutas locales difieren. La comprobación crea una instalación desechable de MySQL en `127.0.0.1:13306` y despliega el WAR en Tomcat en `127.0.0.1:18091`. Ambos procesos se cierran al terminar. El servidor habitual de MySQL conserva su esquema y sus datos.

Las verificaciones con H2 se ejecutan por separado, según el README. Las pruebas de concurrencia, triggers, permisos JDBC y renderizado JSP de esta guía usan MySQL y Tomcat reales. La prueba web verifica los nueve accesos para cada uno de los ocho perfiles y comprueba altas, ediciones y transacciones sobre datos sintéticos.

## Archivos del sistema

| Parte | Ubicación |
| --- | --- |
| Rutas y control de acceso | `src/main/java/pe/gob/sigmud/controller/` y `src/main/java/pe/gob/sigmud/filter/SeguridadFilter.java` |
| Reglas de negocio | `src/main/java/pe/gob/sigmud/service/` |
| Consultas y transacciones | `src/main/java/pe/gob/sigmud/dao/impl/`, `Jdbc.java` y `AlcanceAreas.java` |
| Navegación, vistas y componentes | `src/main/webapp/WEB-INF/views/acceso/`, `src/main/webapp/WEB-INF/views/gestion/`, `assets/css/panel.css` y `assets/js/panel.js` |
| Actualizaciones incrementales | `database/06_nombres_visibles.sql`, `07_permisos_sectores.sql` y `08_permisos_alta_citas.sql` |
| Verificación integral | `scripts/ProbarIntegracion.ps1`, `scripts/ComprobarWeb.ps1`, `scripts/ComprobarFlujosWeb.ps1`, `scripts/ServidorPruebas.java` y `src/test/java/pe/gob/sigmud/service/ComprobarGestionMySql.java` |
