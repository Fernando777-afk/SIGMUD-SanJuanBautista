# DISEÑO TÉCNICO — SIGMUD (propuesta inicial)

## Arquitectura
Navegador (JSP + JSTL + Bootstrap 5 + JS) → Servlet controlador → Service (validaciones y transacciones) → DAO (JDBC/PreparedStatement) → MySQL Server 8.0. Tomcat 10.1 ejecuta el WAR. El acceso a JSP internos va por controladores y vistas bajo `WEB-INF/views`.

## Paquetes
`pe.gob.sigmud.model`, `.dao`, `.dao.impl`, `.controller`, `.service`, `.util`, `.filter`, `.listener`.

## Organización propuesta
```
SIGMUD/
├── pom.xml
├── src/main/java/pe/gob/sigmud/{model,dao,dao/impl,controller,service,util,filter,listener}/
├── src/main/webapp/
│   ├── assets/{css,js,img}/
│   ├── WEB-INF/views/{login,dashboard,contribuyentes,tramites,reclamos,citas,mercado,almacen,reportes}/
│   └── index.jsp
└── database/{sigmud.sql,datos_demo.sql}
```

## Datos propuestos (a validar antes del DDL)
`roles`, `usuarios`, `contribuyentes`, `areas`, `funcionarios`, `tramites`, `historial_tramites`, `reclamos_emergencias`, `citas`, `mercados`, `puestos`, `asignaciones_puestos`, `materiales`, `movimientos_stock`. Agregar entidades únicamente por necesidad demostrable de un RF, con FK, restricciones, índices y eliminación lógica donde corresponda. Diseñar normalización hasta 3FN.

## Controles clave
- Login: PBKDF2-HMAC-SHA-256 con 600 000 iteraciones y una sal aleatoria por contraseña. El DAO guarda el hash e incrementa el contador de forma transaccional. Cinco intentos fallidos bloquean la cuenta durante 15 minutos. Un acceso correcto reinicia el contador.
- Sesión: `HttpSession` con renovación de identificador al entrar, cierre por POST, token CSRF y tiempo de inactividad de 30 minutos. En cada ruta protegida se verifica la cuenta, su rol y los permisos actuales. Un cambio de contraseña o la deshabilitación revoca el acceso de la sesión.
- Cuentas: `/usuarios` exige el rol `ADMINISTRADOR` y el permiso `SEGURIDAD_ADMINISTRAR`. Estados, roles, búsqueda y paginación se resuelven en SQL. El administrador puede asignar áreas y consultar permisos. Las cuentas reservadas conservan el identificador y el rol y la habilitación de la cuenta administradora en uso se comprueban bajo bloqueo.
- La vista `/inicio` usa la identidad validada por el filtro para escoger un panel por rol y consultar sus indicadores, tablas y accesos autorizados. Se mantiene el alcance de trámites, reclamos y citas por área, con alcance conjunto de Fiscalización y Defensa Civil y global para el administrador. Rentas inicia desde los vínculos prediales y Registro Civil desde el padrón de personas naturales y su propia agenda. Las vistas de inicio muestran referencias y estados, con los números de documento enmascarados en el panel de Registro Civil.
- Contribuyentes: validación de DNI, RUC, teléfono y tipo de persona en el servicio. La baja lógica mantiene expedientes y predios.
- Trámites: expediente y asiento inicial en una transacción. Cada actualización agrega un movimiento tras bloquear el expediente y comparar el movimiento vigente. La consulta usa el área del último movimiento. El historial se descarga como TXT.
- Citas: los triggers verifican el solapamiento por ventanilla y funcionario. La reprogramación cancela la cita anterior y registra la siguiente dentro de una sola transacción.
- Mercados: la ocupación se deriva de la asignación vigente. La clave única impide dos ocupaciones simultáneas. La liberación conserva el historial.
- Almacén: los triggers mantienen el stock a partir de movimientos inmutables. La aplicación escribe el movimiento y el saldo cambia dentro de la transacción. Los avisos de vencimiento usan un horizonte de 30 días.
- Reclamos: la cola visible se actualiza cada 30 segundos por una solicitud HTTP. El cambio de atención comprueba el área y el estado vigentes de la fila.
- Reportes y búsquedas: cada consulta exige permiso sobre el módulo de origen. Trámites, reclamos y citas aplican el alcance por área. Los reportes usan CSV escapado y hoja de impresión, y los resultados de búsqueda enlazan sus fichas.

La conexión JDBC toma las tres variables de entorno como conjunto prioritario. Si ninguna está definida, resuelve un localizador absoluto bajo el perfil de Windows de la JVM y lee el archivo privado de la instalación. La configuración y la habilitación inicial de cuentas son utilidades de consola ajenas al WAR.

## Diseño visual
Sistema institucional de uso interno; sidebar legible y responsive, encabezado con usuario/rol, dashboard con indicadores, tablas filtrables, formularios con etiquetas, fechas modernas y mensajes de validación. No usar emblemas ni logotipos institucionales no autorizados como si fueran oficiales.

El login usa la fotografía y el escudo proporcionados para San Juan Bautista. En móvil, el formulario se presenta antes de la fotografía. El escudo conserva el JPEG original y también identifica el menú lateral. Las vistas autenticadas comparten el menú por áreas, la barra superior, los iconos SVG locales y los componentes de `acceso.css`, `panel.css` y `panel.js` y la plantilla de tablas `tabla-municipal.jspf`. El menú se abre como panel superpuesto en móvil y muestra los enlaces autorizados por el perfil.

En Registro Civil, Fichas de personas busca por nombre, DNI o RUC entre personas naturales. El acceso secundario Consulta general conserva tipos de persona y predios bajo el mismo permiso. Las agendas muestran acciones de reserva si hay ventanillas con funcionarios activos habilitados. Los filtros de Búsquedas y Reportes se ajustan al tipo seleccionado y las consultas por área mantienen su alcance en JDBC.

## Documentación y normas
Marco referencial: Ley 27972, Ley 29733 y su reglamento vigente, D. Leg. 1412 y reglamento, Ley 27806/TUO, ROF y TUPA municipales verificados. No interpretar el prototipo como plataforma legalmente habilitada para trámites oficiales.
