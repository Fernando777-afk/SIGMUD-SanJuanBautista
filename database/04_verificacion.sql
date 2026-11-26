-- Consultas de solo lectura. Ejecutar tras el esquema y los catálogos.
-- Los parámetros de ejemplo corresponden a las fechas ficticias de 03_datos_demo.sql.
USE sigmud_db;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @sigmud_desde = '2030-06-01 00:00:00';
SET @sigmud_hasta = '2030-07-01 00:00:00';
SET @sigmud_fecha_referencia = '2030-06-15';
SET @sigmud_horizonte_dias = 30;
SET @sigmud_codigo_predio = 'DEMO-PREDIO-01';

START TRANSACTION READ ONLY;

SELECT DATABASE() AS esquema, VERSION() AS version_mysql,
       @@SESSION.sql_mode AS modo_sql, @@SESSION.foreign_key_checks AS claves_foraneas_activas;

SELECT default_character_set_name AS juego_caracteres, default_collation_name AS collation_esquema
FROM information_schema.schemata
WHERE schema_name = DATABASE();

-- Se esperan 20 tablas InnoDB, seis triggers y la rutina de validación de citas.
SELECT table_name, engine, table_collation
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
ORDER BY table_name;

SELECT table_name, constraint_type, COUNT(*) AS total
FROM information_schema.table_constraints
WHERE table_schema = DATABASE()
GROUP BY table_name, constraint_type
ORDER BY table_name, constraint_type;

SELECT table_name, constraint_name, enforced
FROM information_schema.table_constraints
WHERE table_schema = DATABASE() AND constraint_type = 'CHECK'
ORDER BY table_name, constraint_name;

SELECT table_name, constraint_name, column_name, referenced_table_name, referenced_column_name
FROM information_schema.key_column_usage
WHERE table_schema = DATABASE() AND referenced_table_name IS NOT NULL
ORDER BY table_name, constraint_name, ordinal_position;

SELECT trigger_name, event_object_table, action_timing, event_manipulation
FROM information_schema.triggers
WHERE trigger_schema = DATABASE()
ORDER BY trigger_name;

SELECT routine_name, routine_type, sql_data_access
FROM information_schema.routines
WHERE routine_schema = DATABASE() AND routine_name = 'validar_reserva_cita';

-- Catálogos y matriz de permisos. Se esperan ocho roles con permisos.
SELECT r.codigo AS rol, COUNT(rp.permiso_id) AS permisos_asignados
FROM roles AS r
LEFT JOIN roles_permisos AS rp ON rp.rol_id = r.id
GROUP BY r.id, r.codigo
ORDER BY r.codigo;

SELECT r.codigo AS rol, p.codigo AS permiso
FROM roles_permisos AS rp
JOIN roles AS r ON r.id = rp.rol_id
JOIN permisos AS p ON p.id = rp.permiso_id
ORDER BY r.codigo, p.codigo;

-- Integridad. Los resultados de las seis consultas siguientes deben estar vacíos.
SELECT u.nombre_usuario
FROM usuarios AS u
WHERE (u.activo = 1 AND (u.hash_contrasena IS NULL OR CHAR_LENGTH(TRIM(u.hash_contrasena)) = 0))
   OR (u.nombre_usuario LIKE 'demo.%' AND (u.activo <> 0 OR u.hash_contrasena IS NOT NULL));

SELECT t.numero_expediente
FROM tramites AS t
WHERE NOT EXISTS (SELECT 1 FROM historial_tramites AS h WHERE h.tramite_id = t.id);

SELECT a.codigo_cita AS cita_a, b.codigo_cita AS cita_b
FROM citas AS a
JOIN citas AS b ON b.id > a.id
    AND (a.funcionario_id = b.funcionario_id OR a.ventanilla_id = b.ventanilla_id)
    AND a.inicio < b.fin AND b.inicio < a.fin
WHERE a.inicio_reservado IS NOT NULL AND b.inicio_reservado IS NOT NULL;

SELECT p.numero AS puesto, a.id AS asignacion_a, b.id AS asignacion_b
FROM asignaciones_puestos AS a
JOIN asignaciones_puestos AS b ON b.puesto_id = a.puesto_id AND b.id > a.id
    AND (a.liberado_en IS NULL OR b.asignado_en < a.liberado_en)
    AND (b.liberado_en IS NULL OR a.asignado_en < b.liberado_en)
JOIN puestos AS p ON p.id = a.puesto_id;

SELECT m.codigo AS mercado, p.numero AS puesto, a.id AS asignacion_vigente
FROM asignaciones_puestos AS a
JOIN puestos AS p ON p.id = a.puesto_vigente_id
JOIN mercados AS m ON m.id = p.mercado_id
WHERE p.baja_en IS NOT NULL OR m.baja_en IS NOT NULL;

SELECT m.codigo AS material, l.codigo_lote, l.stock AS saldo_guardado,
       COALESCE(SUM(CASE ms.tipo WHEN 'INGRESO' THEN ms.cantidad WHEN 'SALIDA' THEN -ms.cantidad END), 0)
           AS saldo_por_movimientos
FROM lotes_material AS l
JOIN materiales AS m ON m.id = l.material_id
LEFT JOIN movimientos_stock AS ms ON ms.lote_id = l.id
GROUP BY l.id, m.codigo, l.codigo_lote, l.stock
HAVING saldo_guardado <> saldo_por_movimientos;

-- Consulta de expedientes con su último estado y área.
SELECT t.numero_expediente, c.nombre_razon_social, a.codigo AS area, h.estado, h.registrado_en
FROM tramites AS t
JOIN contribuyentes AS c ON c.id = t.contribuyente_id
JOIN historial_tramites AS h ON h.id = (
    SELECT MAX(hu.id) FROM historial_tramites AS hu WHERE hu.tramite_id = t.id
)
JOIN areas AS a ON a.id = h.area_id
ORDER BY t.numero_expediente;

-- Cola de atención ordenada por prioridad y antigüedad.
SELECT re.codigo_caso, re.tipo, a.codigo AS area, re.estado, re.prioridad, re.registrado_en
FROM reclamos_emergencias AS re
JOIN areas AS a ON a.id = re.area_id
WHERE re.estado IN ('PENDIENTE', 'EN_ATENCION')
ORDER BY re.prioridad DESC, re.registrado_en, re.id;

-- Agenda por área, ventanilla y funcionario.
SELECT c.codigo_cita, a.codigo AS area, v.codigo AS ventanilla, u.nombre_visible AS funcionario,
       c.inicio, c.fin, c.estado
FROM citas AS c
JOIN ventanillas AS v ON v.id = c.ventanilla_id
JOIN areas AS a ON a.id = v.area_id
JOIN usuarios AS u ON u.id = c.funcionario_id
WHERE c.inicio >= @sigmud_desde AND c.inicio < @sigmud_hasta
ORDER BY c.inicio, c.codigo_cita;

-- Reportes. El intervalo del período incluye el inicio y excluye el fin.
SELECT a.codigo AS area, COUNT(DISTINCT h.tramite_id) AS expedientes_atendidos
FROM historial_tramites AS h
JOIN areas AS a ON a.id = h.area_id
WHERE h.estado = 'ATENDIDO' AND h.registrado_en >= @sigmud_desde AND h.registrado_en < @sigmud_hasta
GROUP BY a.id, a.codigo
ORDER BY a.codigo;

SELECT m.codigo AS mercado, COUNT(p.id) AS puestos_habilitados, COUNT(ap.id) AS puestos_ocupados,
       ROUND(100.0 * COUNT(ap.id) / NULLIF(COUNT(p.id), 0), 2) AS porcentaje_ocupacion
FROM mercados AS m
LEFT JOIN puestos AS p ON p.mercado_id = m.id AND p.baja_en IS NULL
LEFT JOIN asignaciones_puestos AS ap ON ap.puesto_vigente_id = p.id
WHERE m.baja_en IS NULL
GROUP BY m.id, m.codigo
ORDER BY m.codigo;

SELECT m.codigo AS material, m.nombre, COALESCE(SUM(l.stock), 0) AS stock_actual, m.stock_minimo
FROM materiales AS m
LEFT JOIN lotes_material AS l ON l.material_id = m.id
WHERE m.baja_en IS NULL
GROUP BY m.id, m.codigo, m.nombre, m.stock_minimo
HAVING stock_actual <= m.stock_minimo
ORDER BY stock_actual, m.codigo;

SELECT prioridad, COUNT(*) AS reclamos_atendidos
FROM reclamos_emergencias
WHERE tipo = 'RECLAMO' AND estado = 'ATENDIDO'
  AND atendido_en >= @sigmud_desde AND atendido_en < @sigmud_hasta
GROUP BY prioridad
ORDER BY prioridad DESC;

-- Búsquedas. El horizonte de vencimiento se parametriza.
SELECT m.codigo AS material, l.codigo_lote, l.vence_en, l.stock
FROM lotes_material AS l
JOIN materiales AS m ON m.id = l.material_id
WHERE m.baja_en IS NULL AND l.stock > 0
  AND l.vence_en BETWEEN @sigmud_fecha_referencia
      AND DATE_ADD(@sigmud_fecha_referencia, INTERVAL @sigmud_horizonte_dias DAY)
ORDER BY l.vence_en, m.codigo, l.codigo_lote;

SELECT cp.codigo_predio, c.nombre_razon_social
FROM contribuyentes_predios AS cp
JOIN contribuyentes AS c ON c.id = cp.contribuyente_id
WHERE cp.codigo_predio = @sigmud_codigo_predio AND c.baja_en IS NULL
ORDER BY c.nombre_razon_social, c.id;

COMMIT;
