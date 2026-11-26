-- Ejecutar manualmente desde Workbench con una cuenta administradora.
-- La cuenta se crea bloqueada con una clave aleatoria. Revisar los permisos antes de desbloquearla.
CREATE USER 'sigmud_app'@'127.0.0.1'
    IDENTIFIED WITH caching_sha2_password BY RANDOM PASSWORD
    REQUIRE SSL
    ACCOUNT LOCK;

-- Catálogos iniciales.
GRANT SELECT ON sigmud_db.areas TO 'sigmud_app'@'127.0.0.1';
GRANT SELECT ON sigmud_db.roles TO 'sigmud_app'@'127.0.0.1';
GRANT SELECT ON sigmud_db.permisos TO 'sigmud_app'@'127.0.0.1';
GRANT SELECT ON sigmud_db.sectores_mercado TO 'sigmud_app'@'127.0.0.1';

-- Acceso. La revocación de permisos elimina únicamente la asociación.
GRANT SELECT, INSERT, DELETE ON sigmud_db.roles_permisos TO 'sigmud_app'@'127.0.0.1';
GRANT SELECT,
    INSERT (rol_id, area_id, nombre_usuario, nombre_visible, hash_contrasena, activo),
    UPDATE (rol_id, area_id, nombre_usuario, nombre_visible, hash_contrasena, activo, intentos_fallidos, bloqueado_hasta)
ON sigmud_db.usuarios TO 'sigmud_app'@'127.0.0.1';

-- Contribuyentes con baja lógica. Los códigos de predio son asociaciones.
GRANT SELECT,
    INSERT (tipo_persona, dni, ruc, nombre_razon_social, telefono, direccion, fecha_nacimiento),
    UPDATE (tipo_persona, dni, ruc, nombre_razon_social, telefono, direccion, fecha_nacimiento, baja_en)
ON sigmud_db.contribuyentes TO 'sigmud_app'@'127.0.0.1';
GRANT SELECT, INSERT, DELETE ON sigmud_db.contribuyentes_predios TO 'sigmud_app'@'127.0.0.1';

-- El área y el estado del expediente se gestionan en el historial.
-- UPDATE sobre una columna editable permite bloquear el expediente con SELECT FOR UPDATE.
GRANT SELECT,
    INSERT (numero_expediente, contribuyente_id, registrado_por, tipo_tramite, descripcion),
    UPDATE (descripcion)
ON sigmud_db.tramites TO 'sigmud_app'@'127.0.0.1';
GRANT SELECT,
    INSERT (tramite_id, area_id, estado, registrado_por, observacion)
ON sigmud_db.historial_tramites TO 'sigmud_app'@'127.0.0.1';
GRANT SELECT,
    INSERT (codigo_caso, tipo, contribuyente_id, area_id, registrado_por, descripcion, referencia_ubicacion, prioridad),
    UPDATE (area_id, prioridad, estado, atendido_en)
ON sigmud_db.reclamos_emergencias TO 'sigmud_app'@'127.0.0.1';

-- Agenda. Reprogramar consiste en cancelar y registrar la cita sustituta.
GRANT SELECT, INSERT (area_id, codigo, nombre), UPDATE (nombre, baja_en)
ON sigmud_db.ventanillas TO 'sigmud_app'@'127.0.0.1';
GRANT SELECT, INSERT (ventanilla_id, funcionario_id), UPDATE (baja_en)
ON sigmud_db.ventanillas_funcionarios TO 'sigmud_app'@'127.0.0.1';
GRANT SELECT,
    INSERT (codigo_cita, contribuyente_id, ventanilla_id, funcionario_id, registrado_por, inicio, fin, motivo),
    UPDATE (estado, motivo)
ON sigmud_db.citas TO 'sigmud_app'@'127.0.0.1';

-- Mercados. La fecha y el responsable de liberación cierran la asignación.
GRANT SELECT, INSERT (codigo, nombre), UPDATE (nombre, baja_en)
ON sigmud_db.mercados TO 'sigmud_app'@'127.0.0.1';
GRANT SELECT, INSERT (mercado_id, sector_id, numero), UPDATE (numero, baja_en)
ON sigmud_db.puestos TO 'sigmud_app'@'127.0.0.1';
GRANT SELECT,
    INSERT (puesto_id, comerciante_id, giro_comercial, asignado_por),
    UPDATE (liberado_por, liberado_en)
ON sigmud_db.asignaciones_puestos TO 'sigmud_app'@'127.0.0.1';

-- Almacén. El trigger existente aplica el saldo con los privilegios de su definidor.
GRANT SELECT, INSERT (codigo, nombre, unidad_medida, stock_minimo), UPDATE (nombre, stock_minimo, baja_en)
ON sigmud_db.materiales TO 'sigmud_app'@'127.0.0.1';
GRANT SELECT, INSERT (material_id, codigo_lote, vence_en), UPDATE (vence_en)
ON sigmud_db.lotes_material TO 'sigmud_app'@'127.0.0.1';
GRANT SELECT,
    INSERT (codigo_movimiento, lote_id, tipo, cantidad, area_solicitante_id, registrado_por, motivo)
ON sigmud_db.movimientos_stock TO 'sigmud_app'@'127.0.0.1';
