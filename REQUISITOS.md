# REQUISITOS — SIGMUD

## Fuente y prioridad
**Fuente principal:** `SIGMUD_Municipalidades_Huamanga(1).pdf`, págs. 3–6 (módulos y RF01–RF10), págs. 6–8 (requisitos de calidad, arquitectura y entregables). Se cambia únicamente la modalidad **escritorio → web** por indicación del usuario. **Fuente secundaria:** `Esquema_Monografia_Proyecto_Final_SIGMUD.pdf`, usada para MVC/DAO/Servlet/JSP/Bootstrap/JSTL/Ajax y documentación.

## Contexto
Prototipo académico interno para la Municipalidad Distrital de San Juan Bautista (Huamanga, Ayacucho). Usuarios: administrador y personal municipal. Sin conexión a sistemas oficiales ni tratamiento de datos reales.

## Requisitos funcionales adaptados a web
- RF01 — Inicio de sesión, roles del personal: mesa de partes, rentas, fiscalización, almacén, administrador; manejo de credenciales inválidas.
- RF02 — Registrar, buscar, actualizar y dar de baja lógica a contribuyentes y vecinos; validar DNI/RUC, teléfono y otros campos aplicables.
- RF03 — Registrar y consultar expedientes por área asociados a contribuyentes; exportar historial a archivo.
- RF04 — Registrar reclamos/emergencias con prioridad; actualizar cola automáticamente sin bloquear la experiencia web (polling/Ajax controlado, en vez de Thread de Swing).
- RF05 — Agendar citas por ventanilla, área y funcionario; impedir cruce de horarios.
- RF06 — Asignar/liberar puestos de mercado por sector con transacción atómica y control de ocupación.
- RF07 — Registrar ingresos/salidas del almacén y mantener stock mediante transacciones; impedir stock negativo.
- RF08 — Reportes por área y periodo, ocupación de puestos, stock crítico y reclamos por prioridad; SQL parametrizado y exportación.
- RF09 — Filtrar/ordenar listados con List, Map, Comparator/Comparable y Stream API de Java.
- RF10 — Mensajes comprensibles ante errores de validación o conexión sin exponer detalles técnicos.

## Requisitos no funcionales
Diseño web consistente y adaptable, consultas eficientes y tareas largas no bloqueantes, transacciones ACID, contraseñas con hash seguro, autorización por rol, sesiones HTTP, `PreparedStatement`, Java 17, Maven, mantenimiento por capas y pruebas de validación, seguridad y flujos críticos.

## Exclusiones iniciales
Sin aplicación Swing, integraciones reales con RENIEC/SUNAT, pagos reales, certificados oficiales, trámites con validez legal o portal ciudadano público salvo solicitud posterior.

## Criterios de aceptación
Todas las funciones RF01–RF10 verificables desde navegador, persistencia real en MySQL, permisos respetados, transacciones reversibles ante error, scripts reproducibles y compilación WAR con Maven.
