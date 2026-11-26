-- Ajusta exclusivamente las etiquetas de las ocho cuentas instaladas por 03_datos_demo.sql.
-- La coincidencia con el nombre anterior evita sobrescribir personalizaciones.
USE sigmud_db;
START TRANSACTION;
UPDATE usuarios SET nombre_visible = 'Administración'
WHERE nombre_usuario = 'demo.admin' AND nombre_visible = 'Administración de prueba';
UPDATE usuarios SET nombre_visible = 'Mesa de Partes'
WHERE nombre_usuario = 'demo.mesa' AND nombre_visible = 'Mesa de Partes de prueba';
UPDATE usuarios SET nombre_visible = 'Rentas'
WHERE nombre_usuario = 'demo.rentas' AND nombre_visible = 'Rentas de prueba';
UPDATE usuarios SET nombre_visible = 'Fiscalización y Defensa Civil'
WHERE nombre_usuario = 'demo.defensa' AND nombre_visible = 'Defensa Civil de prueba';
UPDATE usuarios SET nombre_visible = 'Atención al Vecino'
WHERE nombre_usuario = 'demo.atencion' AND nombre_visible = 'Atención al Vecino de prueba';
UPDATE usuarios SET nombre_visible = 'Mercados'
WHERE nombre_usuario = 'demo.mercados' AND nombre_visible = 'Mercados de prueba';
UPDATE usuarios SET nombre_visible = 'Almacén y Logística'
WHERE nombre_usuario = 'demo.almacen' AND nombre_visible = 'Almacén de prueba';
UPDATE usuarios SET nombre_visible = 'Registro Civil'
WHERE nombre_usuario = 'demo.registro' AND nombre_visible = 'Registro Civil de prueba';
COMMIT;
