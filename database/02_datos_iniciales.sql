-- Catálogos funcionales del prototipo. La matriz debe revisarse antes de habilitar cuentas.
USE sigmud_db;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

START TRANSACTION;

INSERT INTO areas (codigo, nombre) VALUES
    ('MESA_PARTES', 'Mesa de Partes'),
    ('RENTAS', 'Rentas'),
    ('DESARROLLO_URBANO', 'Desarrollo Urbano'),
    ('FISCALIZACION', 'Fiscalización'),
    ('DEFENSA_CIVIL', 'Defensa Civil'),
    ('ATENCION_VECINO', 'Atención al Vecino'),
    ('MERCADOS', 'Mercados'),
    ('ALMACEN', 'Almacén y Logística'),
    ('REGISTRO_CIVIL', 'Registro Civil');

INSERT INTO roles (codigo, nombre) VALUES
    ('ADMINISTRADOR', 'Administrador'),
    ('MESA_PARTES', 'Mesa de Partes'),
    ('RENTAS', 'Rentas'),
    ('FISCALIZACION_DEFENSA_CIVIL', 'Fiscalización y Defensa Civil'),
    ('ATENCION_VECINO', 'Atención al Vecino'),
    ('MERCADOS', 'Mercados'),
    ('ALMACEN', 'Almacén'),
    ('REGISTRO_CIVIL', 'Registro Civil');

INSERT INTO permisos (codigo, descripcion) VALUES
    ('SEGURIDAD_ADMINISTRAR', 'Administrar usuarios, roles y permisos'),
    ('CONTRIBUYENTES_CONSULTAR', 'Consultar y buscar contribuyentes y vecinos'),
    ('CONTRIBUYENTES_GESTIONAR', 'Registrar, actualizar y dar de baja a contribuyentes y vecinos'),
    ('TRAMITES_CONSULTAR', 'Consultar expedientes y exportar el historial autorizado'),
    ('TRAMITES_REGISTRAR', 'Registrar expedientes'),
    ('TRAMITES_ACTUALIZAR', 'Actualizar el estado o área de un expediente'),
    ('RECLAMOS_CONSULTAR', 'Consultar casos y la cola de atención'),
    ('RECLAMOS_REGISTRAR', 'Registrar reclamos y emergencias'),
    ('RECLAMOS_ATENDER', 'Actualizar la prioridad y registrar la atención de casos'),
    ('CITAS_CONSULTAR', 'Consultar la agenda municipal'),
    ('CITAS_GESTIONAR', 'Programar, actualizar y cancelar citas'),
    ('MERCADOS_CONSULTAR', 'Consultar mercados, puestos y su ocupación'),
    ('MERCADOS_GESTIONAR', 'Gestionar puestos y asignaciones'),
    ('ALMACEN_CONSULTAR', 'Consultar materiales, lotes y movimientos'),
    ('ALMACEN_GESTIONAR', 'Gestionar materiales y registrar movimientos de almacén'),
    ('REPORTES_CONSULTAR', 'Consultar y exportar reportes de los módulos autorizados');

INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT r.id, p.id
FROM roles AS r
CROSS JOIN permisos AS p
WHERE r.codigo = 'ADMINISTRADOR'
   OR (r.codigo = 'MESA_PARTES' AND p.codigo IN (
       'CONTRIBUYENTES_CONSULTAR', 'CONTRIBUYENTES_GESTIONAR',
       'TRAMITES_CONSULTAR', 'TRAMITES_REGISTRAR', 'TRAMITES_ACTUALIZAR', 'REPORTES_CONSULTAR'
   ))
   OR (r.codigo IN ('RENTAS', 'REGISTRO_CIVIL') AND p.codigo IN (
       'CONTRIBUYENTES_CONSULTAR', 'TRAMITES_CONSULTAR', 'TRAMITES_ACTUALIZAR',
       'CITAS_CONSULTAR', 'CITAS_GESTIONAR', 'REPORTES_CONSULTAR'
   ))
   OR (r.codigo = 'FISCALIZACION_DEFENSA_CIVIL' AND p.codigo IN (
       'CONTRIBUYENTES_CONSULTAR', 'TRAMITES_CONSULTAR', 'TRAMITES_ACTUALIZAR',
       'RECLAMOS_CONSULTAR', 'RECLAMOS_REGISTRAR', 'RECLAMOS_ATENDER',
       'CITAS_CONSULTAR', 'CITAS_GESTIONAR', 'REPORTES_CONSULTAR'
   ))
   -- Atención al Vecino atiende reclamos de su área. El servicio delimitará el alcance.
   OR (r.codigo = 'ATENCION_VECINO' AND p.codigo IN (
       'CONTRIBUYENTES_CONSULTAR', 'RECLAMOS_CONSULTAR', 'RECLAMOS_REGISTRAR', 'RECLAMOS_ATENDER',
       'CITAS_CONSULTAR', 'CITAS_GESTIONAR', 'REPORTES_CONSULTAR'
   ))
   OR (r.codigo = 'MERCADOS' AND p.codigo IN (
       'CONTRIBUYENTES_CONSULTAR', 'MERCADOS_CONSULTAR', 'MERCADOS_GESTIONAR', 'REPORTES_CONSULTAR'
   ))
   OR (r.codigo = 'ALMACEN' AND p.codigo IN (
       'ALMACEN_CONSULTAR', 'ALMACEN_GESTIONAR', 'REPORTES_CONSULTAR'
   ));

INSERT INTO sectores_mercado (codigo, nombre) VALUES
    ('ABARROTES', 'Abarrotes'),
    ('CARNES', 'Carnes'),
    ('VERDURAS', 'Verduras'),
    ('ROPA', 'Ropa');

COMMIT;
