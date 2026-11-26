-- Datos ficticios opcionales para probar el esquema tras cargar los catálogos.
-- Usar únicamente en un entorno de pruebas. Las cuentas quedan deshabilitadas y sin hash.
USE sigmud_db;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

START TRANSACTION;

INSERT INTO usuarios (rol_id, area_id, nombre_usuario, nombre_visible, creado_en) VALUES
    ((SELECT id FROM roles WHERE codigo = 'ADMINISTRADOR'),
        NULL, 'demo.admin', 'Administración de prueba', '2030-06-01 08:00:00'),
    ((SELECT id FROM roles WHERE codigo = 'MESA_PARTES'),
        (SELECT id FROM areas WHERE codigo = 'MESA_PARTES'), 'demo.mesa', 'Mesa de Partes de prueba', '2030-06-01 08:00:00'),
    ((SELECT id FROM roles WHERE codigo = 'RENTAS'),
        (SELECT id FROM areas WHERE codigo = 'RENTAS'), 'demo.rentas', 'Rentas de prueba', '2030-06-01 08:00:00'),
    ((SELECT id FROM roles WHERE codigo = 'FISCALIZACION_DEFENSA_CIVIL'),
        (SELECT id FROM areas WHERE codigo = 'DEFENSA_CIVIL'), 'demo.defensa', 'Defensa Civil de prueba', '2030-06-01 08:00:00'),
    ((SELECT id FROM roles WHERE codigo = 'ATENCION_VECINO'),
        (SELECT id FROM areas WHERE codigo = 'ATENCION_VECINO'), 'demo.atencion', 'Atención al Vecino de prueba', '2030-06-01 08:00:00'),
    ((SELECT id FROM roles WHERE codigo = 'MERCADOS'),
        (SELECT id FROM areas WHERE codigo = 'MERCADOS'), 'demo.mercados', 'Mercados de prueba', '2030-06-01 08:00:00'),
    ((SELECT id FROM roles WHERE codigo = 'ALMACEN'),
        (SELECT id FROM areas WHERE codigo = 'ALMACEN'), 'demo.almacen', 'Almacén de prueba', '2030-06-01 08:00:00'),
    ((SELECT id FROM roles WHERE codigo = 'REGISTRO_CIVIL'),
        (SELECT id FROM areas WHERE codigo = 'REGISTRO_CIVIL'), 'demo.registro', 'Registro Civil de prueba', '2030-06-01 08:00:00');

SET @demo_area_rentas = (SELECT id FROM areas WHERE codigo = 'RENTAS');
SET @demo_area_urbano = (SELECT id FROM areas WHERE codigo = 'DESARROLLO_URBANO');
SET @demo_area_defensa = (SELECT id FROM areas WHERE codigo = 'DEFENSA_CIVIL');
SET @demo_area_atencion = (SELECT id FROM areas WHERE codigo = 'ATENCION_VECINO');
SET @demo_area_registro = (SELECT id FROM areas WHERE codigo = 'REGISTRO_CIVIL');
SET @demo_mesa = (SELECT id FROM usuarios WHERE nombre_usuario = 'demo.mesa');
SET @demo_rentas = (SELECT id FROM usuarios WHERE nombre_usuario = 'demo.rentas');
SET @demo_defensa = (SELECT id FROM usuarios WHERE nombre_usuario = 'demo.defensa');
SET @demo_atencion = (SELECT id FROM usuarios WHERE nombre_usuario = 'demo.atencion');
SET @demo_mercados = (SELECT id FROM usuarios WHERE nombre_usuario = 'demo.mercados');
SET @demo_almacen = (SELECT id FROM usuarios WHERE nombre_usuario = 'demo.almacen');
SET @demo_registro = (SELECT id FROM usuarios WHERE nombre_usuario = 'demo.registro');

-- Identificadores sintéticos para verificar formato y unicidad.
INSERT INTO contribuyentes
    (tipo_persona, dni, ruc, nombre_razon_social, registrado_en, baja_en)
VALUES
    ('NATURAL', '00000000', NULL, 'Persona ficticia de prueba A', '2030-06-01 09:00:00', NULL),
    ('JURIDICA', NULL, '00000000000', 'Entidad ficticia de prueba B', '2030-06-01 09:05:00', NULL),
    ('NATURAL', NULL, '00000000001', 'Persona ficticia de prueba C', '2030-06-01 09:10:00', '2030-06-10 09:00:00');

SET @demo_persona = (SELECT id FROM contribuyentes WHERE dni = '00000000');
SET @demo_entidad = (SELECT id FROM contribuyentes WHERE ruc = '00000000000');

INSERT INTO contribuyentes_predios (contribuyente_id, codigo_predio) VALUES
    (@demo_persona, 'DEMO-PREDIO-01'),
    (@demo_entidad, 'DEMO-PREDIO-02');

INSERT INTO tramites
    (numero_expediente, contribuyente_id, registrado_por, tipo_tramite, descripcion, registrado_en)
VALUES
    ('DEMO-EXP-001', @demo_persona, @demo_mesa, 'Solicitud de prueba A', 'Expediente sintético.', '2030-06-10 08:00:00'),
    ('DEMO-EXP-002', @demo_entidad, @demo_mesa, 'Solicitud de prueba B', 'Expediente sintético.', '2030-06-11 08:00:00');

SET @demo_expediente_a = (SELECT id FROM tramites WHERE numero_expediente = 'DEMO-EXP-001');
SET @demo_expediente_b = (SELECT id FROM tramites WHERE numero_expediente = 'DEMO-EXP-002');

INSERT INTO historial_tramites
    (tramite_id, area_id, estado, registrado_por, observacion, registrado_en)
VALUES
    (@demo_expediente_a, @demo_area_rentas, 'RECIBIDO', @demo_mesa, NULL, '2030-06-10 08:00:00'),
    (@demo_expediente_a, @demo_area_rentas, 'EN_TRAMITE', @demo_rentas, NULL, '2030-06-10 10:00:00'),
    (@demo_expediente_a, @demo_area_rentas, 'ATENDIDO', @demo_rentas, 'Atención simulada.', '2030-06-12 15:00:00'),
    (@demo_expediente_b, @demo_area_urbano, 'RECIBIDO', @demo_mesa, NULL, '2030-06-11 08:00:00');

INSERT INTO reclamos_emergencias
    (codigo_caso, tipo, contribuyente_id, area_id, registrado_por, descripcion,
     referencia_ubicacion, prioridad, estado, registrado_en, atendido_en)
VALUES
    ('DEMO-CASO-001', 'RECLAMO', @demo_persona, @demo_area_atencion, @demo_atencion, 'Reclamo simulado.',
        NULL, 'MEDIA', 'ATENDIDO', '2030-06-12 10:00:00', '2030-06-13 12:00:00'),
    ('DEMO-CASO-002', 'EMERGENCIA', NULL, @demo_area_defensa, @demo_defensa, 'Emergencia simulada.',
        'Ubicación ficticia de prueba', 'CRITICA', 'PENDIENTE', '2030-06-14 10:00:00', NULL),
    ('DEMO-CASO-003', 'RECLAMO', @demo_entidad, @demo_area_atencion, @demo_atencion, 'Reclamo simulado en revisión.',
        NULL, 'ALTA', 'EN_ATENCION', '2030-06-14 11:00:00', NULL);

INSERT INTO ventanillas (area_id, codigo, nombre) VALUES
    (@demo_area_rentas, 'DEMO-V1', 'Ventanilla de prueba Rentas A'),
    (@demo_area_rentas, 'DEMO-V2', 'Ventanilla de prueba Rentas B'),
    (@demo_area_registro, 'DEMO-V1', 'Ventanilla de prueba Registro Civil');

SET @demo_ventanilla_rentas_a = (SELECT id FROM ventanillas WHERE area_id = @demo_area_rentas AND codigo = 'DEMO-V1');
SET @demo_ventanilla_rentas_b = (SELECT id FROM ventanillas WHERE area_id = @demo_area_rentas AND codigo = 'DEMO-V2');
SET @demo_ventanilla_registro = (SELECT id FROM ventanillas WHERE area_id = @demo_area_registro AND codigo = 'DEMO-V1');

INSERT INTO ventanillas_funcionarios (ventanilla_id, funcionario_id) VALUES
    (@demo_ventanilla_rentas_a, @demo_rentas),
    (@demo_ventanilla_rentas_b, @demo_rentas),
    (@demo_ventanilla_registro, @demo_registro);

INSERT INTO citas
    (codigo_cita, contribuyente_id, ventanilla_id, funcionario_id, registrado_por,
     inicio, fin, motivo, estado, registrado_en)
VALUES
    ('DEMO-CITA-001', @demo_persona, @demo_ventanilla_rentas_a, @demo_rentas, @demo_atencion,
        '2030-06-15 09:00:00', '2030-06-15 09:30:00', 'Cita simulada cancelada.', 'CANCELADA', '2030-06-13 08:00:00'),
    ('DEMO-CITA-002', @demo_persona, @demo_ventanilla_rentas_a, @demo_rentas, @demo_atencion,
        '2030-06-15 09:00:00', '2030-06-15 09:30:00', 'Cita simulada de reemplazo.', 'PROGRAMADA', '2030-06-14 08:00:00'),
    ('DEMO-CITA-003', @demo_entidad, @demo_ventanilla_rentas_b, @demo_rentas, @demo_atencion,
        '2030-06-15 09:30:00', '2030-06-15 10:00:00', 'Cita simulada consecutiva.', 'PROGRAMADA', '2030-06-14 08:10:00'),
    ('DEMO-CITA-004', @demo_entidad, @demo_ventanilla_registro, @demo_registro, @demo_atencion,
        '2030-06-15 09:00:00', '2030-06-15 09:20:00', 'Cita simulada en otra área.', 'PROGRAMADA', '2030-06-14 08:20:00');

INSERT INTO mercados (codigo, nombre) VALUES ('DEMO-MER-01', 'Mercado ficticio de prueba');
SET @demo_mercado = (SELECT id FROM mercados WHERE codigo = 'DEMO-MER-01');
SET @demo_sector_abarrotes = (SELECT id FROM sectores_mercado WHERE codigo = 'ABARROTES');
SET @demo_sector_verduras = (SELECT id FROM sectores_mercado WHERE codigo = 'VERDURAS');

INSERT INTO puestos (mercado_id, sector_id, numero, baja_en) VALUES
    (@demo_mercado, @demo_sector_abarrotes, 'DEMO-01', NULL),
    (@demo_mercado, @demo_sector_verduras, 'DEMO-02', NULL),
    (@demo_mercado, @demo_sector_abarrotes, 'DEMO-03', '2030-06-10 08:00:00');

SET @demo_puesto = (
    SELECT id FROM puestos WHERE mercado_id = @demo_mercado AND sector_id = @demo_sector_abarrotes AND numero = 'DEMO-01'
);

INSERT INTO asignaciones_puestos
    (puesto_id, comerciante_id, giro_comercial, asignado_por, asignado_en, liberado_por, liberado_en)
VALUES
    (@demo_puesto, @demo_persona, 'Giro comercial simulado A', @demo_mercados,
        '2030-06-02 08:00:00', @demo_mercados, '2030-06-09 08:00:00'),
    (@demo_puesto, @demo_entidad, 'Giro comercial simulado B', @demo_mercados, '2030-06-09 08:00:00', NULL, NULL);

INSERT INTO materiales (codigo, nombre, unidad_medida, stock_minimo) VALUES
    ('DEMO-MAT-001', 'Insumo de prueba con vencimiento', 'unidad', 5.000),
    ('DEMO-MAT-002', 'Material de prueba sin vencimiento', 'unidad', 4.000);

SET @demo_material_a = (SELECT id FROM materiales WHERE codigo = 'DEMO-MAT-001');
SET @demo_material_b = (SELECT id FROM materiales WHERE codigo = 'DEMO-MAT-002');

INSERT INTO lotes_material (material_id, codigo_lote, vence_en) VALUES
    (@demo_material_a, 'DEMO-LOTE-A', '2030-06-20'),
    (@demo_material_b, 'DEMO-GENERAL', NULL);

SET @demo_lote_a = (SELECT id FROM lotes_material WHERE material_id = @demo_material_a AND codigo_lote = 'DEMO-LOTE-A');
SET @demo_lote_b = (SELECT id FROM lotes_material WHERE material_id = @demo_material_b AND codigo_lote = 'DEMO-GENERAL');

-- Los saldos de demo nacen de movimientos, igual que los saldos operativos.
INSERT INTO movimientos_stock
    (codigo_movimiento, lote_id, tipo, cantidad, area_solicitante_id, registrado_por, motivo, registrado_en)
VALUES
    ('DEMO-MOV-001', @demo_lote_a, 'INGRESO', 10.000, NULL, @demo_almacen, 'Ingreso simulado.', '2030-06-05 08:00:00'),
    ('DEMO-MOV-002', @demo_lote_a, 'SALIDA', 7.000, @demo_area_defensa, @demo_almacen, 'Salida simulada.', '2030-06-06 09:00:00'),
    ('DEMO-MOV-003', @demo_lote_b, 'INGRESO', 20.000, NULL, @demo_almacen, 'Ingreso simulado.', '2030-06-05 08:00:00'),
    ('DEMO-MOV-004', @demo_lote_b, 'SALIDA', 2.000, @demo_area_atencion, @demo_almacen, 'Salida simulada.', '2030-06-06 10:00:00');

COMMIT;
