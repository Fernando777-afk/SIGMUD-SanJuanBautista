# Base de datos de SIGMUD

Esquema relacional para MySQL Server 8.0.46. La base `sigmud_db` usa `utf8mb4` y `utf8mb4_unicode_ci`. Para una base existente, consulta las actualizaciones incrementales al final de esta guía.

## Revisión antes de ejecutar

1. Abre los cuatro archivos SQL como texto. Comprueba que todos seleccionan `sigmud_db` y que `01` contiene las tablas y disparadores esperados. Revisa las relaciones y las reglas descritas abajo.
2. Revisa los códigos de área y la matriz de permisos en `02`. Son catálogos funcionales del prototipo. Valida su alineación institucional antes de habilitar cuentas o usarlos con datos operativos.
3. Decide si vas a cargar `03`. Sus personas, códigos, fechas, ubicaciones y horarios son ficticios. Las ocho cuentas demo están deshabilitadas y tienen el hash en `NULL`.
4. Revisa `04` y sus resultados esperados. Sus consultas inspeccionan los metadatos y los datos mediante una transacción de solo lectura.

## Instalación posterior en MySQL Workbench

Cuando se autorice la instalación, abre una conexión al MySQL de desarrollo. Confirma en **Schemas** que existe `sigmud_db` y está vacío. Verifica que la sesión usa modo SQL estricto y que la comprobación de claves foráneas está activa. La cuenta de instalación necesita permiso para crear tablas y disparadores. MySQL aplica los permisos del creador al ejecutar dichos disparadores. [Manual de CREATE TRIGGER](https://dev.mysql.com/doc/refman/8.0/en/create-trigger.html)

En **Query**, activa **Stop Script Execution On Errors**. Abre cada archivo con **File → Open SQL Script** y ejecútalo completo en el siguiente orden. El delimitador de los disparadores se procesa correctamente al ejecutar `01` como un script. [Menús del editor SQL de MySQL Workbench](https://dev.mysql.com/doc/workbench/en/wb-sql-editor-main-menu.html)

| Orden | Archivo | Resultado esperado |
| --- | --- | --- |
| 1 | [01_esquema_sigmud.sql](01_esquema_sigmud.sql) | 20 tablas InnoDB, restricciones, índices y seis disparadores. |
| 2 | [02_datos_iniciales.sql](02_datos_iniciales.sql) | Nueve áreas, ocho roles, 16 permisos con su matriz y cuatro sectores. |
| 3 | [03_datos_demo.sql](03_datos_demo.sql) | Datos ficticios opcionales para comprobar flujos y reportes. |
| 4 | [04_verificacion.sql](04_verificacion.sql) | Metadatos, consultas de integridad, reportes y ejemplos de búsqueda. |

Revisa **Action Output** después de cada archivo. Si falla `02` o `03`, ejecuta `ROLLBACK` en la misma conexión y revisa el error antes de reintentar. Los scripts de datos están pensados para una carga única. Si falla `01`, inspecciona qué objetos se crearon antes de continuar. El DDL de MySQL confirma cada sentencia de creación por separado. [Confirmaciones implícitas de MySQL](https://dev.mysql.com/doc/refman/8.0/en/implicit-commit.html)

## Tablas y relaciones

Cada tabla tiene una clave primaria `id` salvo las tres relaciones indicadas con clave compuesta. El DDL define los tipos, índices, claves foráneas y restricciones de cada columna. Las claves foráneas conservan las referencias históricas.

| Módulo | Tabla | Uso, relaciones y estados |
| --- | --- | --- |
| Acceso | `areas` | Catálogo funcional con código único y baja lógica. |
| Acceso | `roles` | Ocho perfiles internos con código único. |
| Acceso | `permisos` | Catálogo de acciones de la aplicación con código único. |
| Acceso | `roles_permisos` | Relación muchos a muchos. Su clave es `(rol_id, permiso_id)`. |
| Acceso | `usuarios` | Cada cuenta tiene un rol y un área opcional. Incluye hash, intentos, fecha de bloqueo y habilitación de inicio de sesión. |
| Contribuyentes y vecinos | `contribuyentes` | Identidad natural o jurídica, DNI y RUC únicos cuando se informan, datos de contacto y baja lógica. |
| Contribuyentes y vecinos | `contribuyentes_predios` | Asociación de códigos de predio con contribuyentes. Su clave es `(contribuyente_id, codigo_predio)`. El índice inverso permite buscar por predio. |
| Trámite documentario | `tramites` | Expediente único vinculado a un contribuyente y al usuario que lo registra. |
| Trámite documentario | `historial_tramites` | Sigue el área, el estado y el actor de cada evento. Estados `RECIBIDO`, `EN_TRAMITE`, `ATENDIDO` y `ARCHIVADO`. |
| Reclamos y emergencias | `reclamos_emergencias` | Caso de uno de los dos tipos, con área, autor, contribuyente opcional, prioridad y fecha de atención. Estados `PENDIENTE`, `EN_ATENCION`, `ATENDIDO` y `CANCELADO`. |
| Citas | `ventanillas` | Ventanilla con código único dentro de su área y baja lógica. |
| Citas | `ventanillas_funcionarios` | Habilitación de usuarios como funcionarios de una ventanilla. Su clave es `(ventanilla_id, funcionario_id)`. Puede darse de baja. |
| Citas | `citas` | Vincula contribuyente, par habilitado, intervalo y autor. Estados `PROGRAMADA`, `ATENDIDA`, `CANCELADA` y `NO_ASISTIO`. |
| Mercados | `mercados` | Mercado con código único y baja lógica. |
| Mercados | `sectores_mercado` | Catálogo de sectores reutilizable entre mercados. |
| Mercados | `puestos` | Ubica un puesto por mercado y sector. Su número es único dentro de ese par. Admite baja lógica. |
| Mercados | `asignaciones_puestos` | Historial de asignaciones a comerciantes registrados como contribuyentes, con responsables y fechas de asignación y liberación. |
| Almacén | `materiales` | Material con código único, unidad, umbral de stock crítico y baja lógica. |
| Almacén | `lotes_material` | Lotes por material con código único dentro de cada material, vencimiento opcional y saldo. Un material sin control por lote puede usar un lote general. |
| Almacén | `movimientos_stock` | Registro inmutable de ingresos y salidas por lote. Conserva código único, cantidad, motivo, autor y área solicitante cuando aplica. |

El módulo de **reportes** agrega los datos ya registrados. Usa fechas de atención para los expedientes y reclamos, puestos habilitados para la ocupación y la suma por material para el stock crítico. El módulo de **búsquedas y clasificación** usa los índices de documento, predio, nombre, fecha y vencimiento. El servicio aplicará la ordenación y el filtrado en Java cuando corresponda a RF09.

El modelo separa catálogos, entidades e historiales para mantener la tercera forma normal. Se obtiene el área de una cita desde su ventanilla y el estado de un expediente desde su último evento. La ocupación se deriva de la asignación sin fecha de liberación. El saldo del lote es una proyección del registro de movimientos que MySQL mantiene en la misma operación.

## Integridad al integrar JDBC

Las operaciones relacionadas usarán una sola conexión JDBC, sentencias parametrizadas y `setAutoCommit(false)`. Se confirma con `commit()` tras completar el flujo y se aplica `rollback()` al fallar. Si se reintenta por interbloqueo, se repite la transacción completa. Los `DATETIME` representan la hora local del prototipo en `America/Lima`. Configura las conexiones de forma coherente, incluidas las fechas por defecto generadas por MySQL. [Lecturas con bloqueo de InnoDB](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html)

- **Acceso y alcance de datos.** La matriz guarda permisos de la aplicación. El servicio comprobará la habilitación de login, el permiso y el alcance del área cuando corresponda. Reportes y búsquedas heredan los permisos de lectura de sus datos. Las cuentas activas requieren un hash que generará el servicio de autenticación.
- **Contribuyentes.** Una persona natural debe tener DNI o RUC. Una persona jurídica requiere RUC. Los documentos se almacenan como texto con validación de formato y conservan su unicidad tras la baja lógica. El teléfono, cuando se informa, admite de 7 a 15 dígitos y un prefijo `+` opcional. Se normaliza antes de guardar.
- **Expedientes.** Inserta el expediente y su primer evento en la misma transacción. Para agregar eventos bloquea primero el expediente por su clave primaria con `FOR UPDATE`. Lee el estado vigente y añade el siguiente evento. El mayor `id` del historial de ese expediente determina su área y estado actuales.
- **Citas.** Un par ventanilla–funcionario debe existir y estar habilitado para programar. La habilitación de agenda es independiente de la habilitación de login. `fin` debe superar a `inicio`. Dos índices únicos impiden iniciar a la vez citas no canceladas en la misma ventanilla o para el mismo funcionario. Las citas canceladas liberan el intervalo.
- **Mercados.** El índice único sobre `puesto_vigente_id` admite una asignación vigente por puesto. Bloquea primero su mercado con `FOR SHARE` y después el puesto con `FOR UPDATE`. Comprueba que ambos estén habilitados y que el comerciante esté activo al asignar. Usa la fecha y hora del servidor para las asignaciones y liberaciones operativas. Una eventual carga histórica debe comprobar el cruce de intervalos con el puesto bloqueado. Para dar de baja un mercado, adquiere primero su bloqueo exclusivo y comprueba que ya no tenga asignaciones vigentes. La baja de un puesto usa el mismo bloqueo del puesto y la misma comprobación.
- **Almacén.** Crea cada lote con saldo cero. Inserta el movimiento mediante `VALUES` con los identificadores ya resueltos. El disparador actualiza el saldo del lote y rechaza una salida superior al saldo o un movimiento de un material dado de baja. Para un ingreso o salida de varios lotes, procesa sus identificadores en orden y confirma el conjunto en una transacción. Las correcciones son movimientos de compensación. La futura cuenta JDBC debe carecer de permiso de actualización sobre el saldo y de reasignación de un lote a otro material. [Comportamiento transaccional de los disparadores](https://dev.mysql.com/doc/refman/8.0/en/trigger-syntax.html)

### Prevención de cruces de citas con distinto inicio

Al programar o reprogramar, bloquea las filas de las ventanillas implicadas con `SELECT ... FOR UPDATE` en orden de `id`. Después bloquea las filas de los funcionarios en ese mismo orden y comprueba la habilitación del par y las bajas de la ventanilla y del área. Para reprogramar, cancela la cita anterior y reserva la nueva dentro de la misma transacción. Una baja en la habilitación debe respetar el mismo orden de bloqueo.

Con los bloqueos retenidos, busca conflictos primero por funcionario y después por ventanilla. El siguiente ejemplo usa tres parámetros JDBC en orden: identificador del funcionario, fin propuesto e inicio propuesto. Para la segunda búsqueda sustituye `funcionario_id` por `ventanilla_id`. Si alguna lectura devuelve una fila, rechaza el cruce. Ambos extremos usan comparación estricta para permitir citas consecutivas.

```sql
SELECT id
FROM citas
WHERE funcionario_id = ?
  AND inicio_reservado < ?
  AND fin > ?
LIMIT 1
FOR UPDATE;
```

Todas las escrituras de agenda deben pasar por este flujo. El esquema controla los inicios duplicados. La comprobación de intervalos con distinto inicio depende de la transacción JDBC. `04` también detecta cruces si hubo una escritura directa fuera de ese flujo.

## Resultados esperados con los datos demo

- Las seis consultas de integridad deben devolver cero filas.
- La agenda muestra cuatro citas, incluida una cancelada que comparte el inicio con su reemplazo.
- Rentas tiene un expediente atendido en el período de prueba. El único reclamo atendido es de prioridad media.
- El mercado ficticio tiene un puesto ocupado de dos habilitados y muestra 50 % de ocupación.
- El material `DEMO-MAT-001` tiene saldo 3 frente a un umbral 5. Su lote aparece en el listado de próximos vencimientos. El saldo de `DEMO-MAT-002` es 18.
- `DEMO-PREDIO-01` devuelve un contribuyente activo.
## Actualizar una instalación existente

En Workbench, revisa y ejecuta `06_nombres_visibles.sql`, `07_permisos_sectores.sql` y `08_permisos_alta_citas.sql`, en ese orden. Para los dos últimos pasos usa la cuenta administradora que concede permisos en MySQL. El usuario de la aplicación sigue siendo `sigmud_app`.

- **06** cambia únicamente los nombres visibles de los ocho registros de desarrollo cuyo identificador y etiqueta original coincidan. Conserva sus hashes, su habilitación y sus relaciones.
- **07** permite insertar sectores y editar su nombre. El permiso funcional `MERCADOS_GESTIONAR` se verifica en el servidor.
- **08** añade permisos de inserción sobre las dos columnas con las que el DAO fija explícitamente el estado inicial y la fecha de alta de la cita. La reserva continúa usando los triggers de MySQL.

El esquema permanece en `sigmud_db`. Usa los scripts 01 a 05 para una instalación nueva y los [pasos de la guía operativa](../GUIA_OPERATIVA.md) para repetir la demostración.

