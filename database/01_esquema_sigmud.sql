-- SIGMUD | MySQL 8.0.46 | Ejecutar sobre el esquema sigmud_db vacío.
-- La cuenta de instalación debe poder crear tablas, triggers y la rutina de agenda.
USE sigmud_db;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Acceso y áreas funcionales del prototipo.
CREATE TABLE areas (
    id SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(40) NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    baja_en DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_areas_codigo (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE roles (
    id SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(40) NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_roles_codigo (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE permisos (
    id SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(45) NOT NULL,
    descripcion VARCHAR(160) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_permisos_codigo (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE roles_permisos (
    rol_id SMALLINT UNSIGNED NOT NULL,
    permiso_id SMALLINT UNSIGNED NOT NULL,
    PRIMARY KEY (rol_id, permiso_id),
    KEY idx_roles_permisos_permiso (permiso_id),
    CONSTRAINT fk_roles_permisos_rol FOREIGN KEY (rol_id) REFERENCES roles (id),
    CONSTRAINT fk_roles_permisos_permiso FOREIGN KEY (permiso_id) REFERENCES permisos (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE usuarios (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    rol_id SMALLINT UNSIGNED NOT NULL,
    area_id SMALLINT UNSIGNED NULL,
    nombre_usuario VARCHAR(64) NOT NULL,
    nombre_visible VARCHAR(160) NOT NULL,
    hash_contrasena VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    activo TINYINT UNSIGNED NOT NULL DEFAULT 0,
    intentos_fallidos SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    bloqueado_hasta DATETIME(6) NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_usuarios_nombre (nombre_usuario),
    KEY idx_usuarios_rol (rol_id),
    KEY idx_usuarios_area_activo (area_id, activo),
    CONSTRAINT fk_usuarios_rol FOREIGN KEY (rol_id) REFERENCES roles (id),
    CONSTRAINT fk_usuarios_area FOREIGN KEY (area_id) REFERENCES areas (id),
    CONSTRAINT ck_usuarios_activo CHECK (activo IN (0, 1)),
    CONSTRAINT ck_usuarios_hash_activo CHECK (
        activo = 0 OR (hash_contrasena IS NOT NULL AND CHAR_LENGTH(TRIM(hash_contrasena)) > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Registro de contribuyentes y vecinos. Los documentos conservan sus ceros iniciales.
CREATE TABLE contribuyentes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tipo_persona ENUM('NATURAL', 'JURIDICA') NOT NULL,
    dni VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ruc VARCHAR(11) CHARACTER SET ascii COLLATE ascii_bin NULL,
    nombre_razon_social VARCHAR(180) NOT NULL,
    telefono VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NULL,
    direccion VARCHAR(220) NULL,
    fecha_nacimiento DATE NULL,
    registrado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    baja_en DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_contribuyentes_dni (dni),
    UNIQUE KEY uq_contribuyentes_ruc (ruc),
    KEY idx_contribuyentes_activos_nombre (baja_en, nombre_razon_social, id),
    CONSTRAINT ck_contribuyentes_documento CHECK (
        (tipo_persona = 'NATURAL' AND (dni IS NOT NULL OR ruc IS NOT NULL))
        OR (tipo_persona = 'JURIDICA' AND dni IS NULL AND ruc IS NOT NULL AND fecha_nacimiento IS NULL)
    ),
    CONSTRAINT ck_contribuyentes_dni CHECK (dni IS NULL OR REGEXP_LIKE(dni, '^[0-9]{8}$', 'c')),
    CONSTRAINT ck_contribuyentes_ruc CHECK (ruc IS NULL OR REGEXP_LIKE(ruc, '^[0-9]{11}$', 'c')),
    CONSTRAINT ck_contribuyentes_telefono CHECK (
        telefono IS NULL OR (
            REGEXP_LIKE(telefono, '^[+]?[0-9]{7,15}$', 'c')
            AND NOT REGEXP_LIKE(telefono, '[[:space:]]', 'c')
        )
    ),
    CONSTRAINT ck_contribuyentes_baja CHECK (baja_en IS NULL OR baja_en >= registrado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE contribuyentes_predios (
    contribuyente_id BIGINT UNSIGNED NOT NULL,
    codigo_predio VARCHAR(40) NOT NULL,
    PRIMARY KEY (contribuyente_id, codigo_predio),
    KEY idx_contribuyentes_predios_codigo (codigo_predio),
    CONSTRAINT fk_contribuyentes_predios_contribuyente
        FOREIGN KEY (contribuyente_id) REFERENCES contribuyentes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- La última entrada del historial es la fuente del área y del estado actuales.
CREATE TABLE tramites (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    numero_expediente VARCHAR(40) NOT NULL,
    contribuyente_id BIGINT UNSIGNED NOT NULL,
    registrado_por BIGINT UNSIGNED NOT NULL,
    tipo_tramite VARCHAR(120) NOT NULL,
    descripcion TEXT NOT NULL,
    registrado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_tramites_numero (numero_expediente),
    KEY idx_tramites_contribuyente_fecha (contribuyente_id, registrado_en),
    KEY idx_tramites_fecha (registrado_en),
    KEY idx_tramites_registrado_por (registrado_por),
    CONSTRAINT fk_tramites_contribuyente FOREIGN KEY (contribuyente_id) REFERENCES contribuyentes (id),
    CONSTRAINT fk_tramites_usuario FOREIGN KEY (registrado_por) REFERENCES usuarios (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE historial_tramites (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tramite_id BIGINT UNSIGNED NOT NULL,
    area_id SMALLINT UNSIGNED NOT NULL,
    estado ENUM('RECIBIDO', 'EN_TRAMITE', 'ATENDIDO', 'ARCHIVADO') NOT NULL,
    registrado_por BIGINT UNSIGNED NOT NULL,
    observacion VARCHAR(500) NULL,
    registrado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_historial_tramites_tramite (tramite_id, id),
    KEY idx_historial_tramites_area_fecha (area_id, registrado_en),
    KEY idx_historial_tramites_estado_fecha (estado, registrado_en, area_id),
    KEY idx_historial_tramites_usuario (registrado_por),
    CONSTRAINT fk_historial_tramites_tramite FOREIGN KEY (tramite_id) REFERENCES tramites (id),
    CONSTRAINT fk_historial_tramites_area FOREIGN KEY (area_id) REFERENCES areas (id),
    CONSTRAINT fk_historial_tramites_usuario FOREIGN KEY (registrado_por) REFERENCES usuarios (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE reclamos_emergencias (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo_caso VARCHAR(40) NOT NULL,
    tipo ENUM('RECLAMO', 'EMERGENCIA') NOT NULL,
    contribuyente_id BIGINT UNSIGNED NULL,
    area_id SMALLINT UNSIGNED NOT NULL,
    registrado_por BIGINT UNSIGNED NOT NULL,
    descripcion TEXT NOT NULL,
    referencia_ubicacion VARCHAR(220) NULL,
    prioridad ENUM('BAJA', 'MEDIA', 'ALTA', 'CRITICA') NOT NULL,
    estado ENUM('PENDIENTE', 'EN_ATENCION', 'ATENDIDO', 'CANCELADO') NOT NULL DEFAULT 'PENDIENTE',
    registrado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    atendido_en DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_reclamos_casos_codigo (codigo_caso),
    KEY idx_reclamos_cola (estado, prioridad DESC, registrado_en, id),
    KEY idx_reclamos_atendidos (estado, atendido_en, prioridad),
    KEY idx_reclamos_contribuyente (contribuyente_id),
    KEY idx_reclamos_area (area_id),
    KEY idx_reclamos_usuario (registrado_por),
    CONSTRAINT fk_reclamos_contribuyente FOREIGN KEY (contribuyente_id) REFERENCES contribuyentes (id),
    CONSTRAINT fk_reclamos_area FOREIGN KEY (area_id) REFERENCES areas (id),
    CONSTRAINT fk_reclamos_usuario FOREIGN KEY (registrado_por) REFERENCES usuarios (id),
    CONSTRAINT ck_reclamos_atencion CHECK (
        (estado = 'ATENDIDO' AND atendido_en IS NOT NULL AND atendido_en >= registrado_en)
        OR (estado <> 'ATENDIDO' AND atendido_en IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Citas. El área se obtiene desde la ventanilla.
CREATE TABLE ventanillas (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    area_id SMALLINT UNSIGNED NOT NULL,
    codigo VARCHAR(35) NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    baja_en DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ventanillas_area_codigo (area_id, codigo),
    CONSTRAINT fk_ventanillas_area FOREIGN KEY (area_id) REFERENCES areas (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ventanillas_funcionarios (
    ventanilla_id INT UNSIGNED NOT NULL,
    funcionario_id BIGINT UNSIGNED NOT NULL,
    baja_en DATETIME(6) NULL,
    PRIMARY KEY (ventanilla_id, funcionario_id),
    KEY idx_ventanillas_funcionarios_usuario (funcionario_id),
    CONSTRAINT fk_ventanillas_funcionarios_ventanilla FOREIGN KEY (ventanilla_id) REFERENCES ventanillas (id),
    CONSTRAINT fk_ventanillas_funcionarios_usuario FOREIGN KEY (funcionario_id) REFERENCES usuarios (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE citas (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo_cita VARCHAR(40) NOT NULL,
    contribuyente_id BIGINT UNSIGNED NOT NULL,
    ventanilla_id INT UNSIGNED NOT NULL,
    funcionario_id BIGINT UNSIGNED NOT NULL,
    registrado_por BIGINT UNSIGNED NOT NULL,
    inicio DATETIME(6) NOT NULL,
    fin DATETIME(6) NOT NULL,
    motivo VARCHAR(240) NOT NULL,
    estado ENUM('PROGRAMADA', 'ATENDIDA', 'CANCELADA', 'NO_ASISTIO') NOT NULL DEFAULT 'PROGRAMADA',
    registrado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    inicio_reservado DATETIME(6) GENERATED ALWAYS AS (
        CASE WHEN estado <> 'CANCELADA' THEN inicio ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_citas_codigo (codigo_cita),
    UNIQUE KEY uq_citas_funcionario_inicio (funcionario_id, inicio_reservado),
    UNIQUE KEY uq_citas_ventanilla_inicio (ventanilla_id, inicio_reservado),
    KEY idx_citas_habilitacion (ventanilla_id, funcionario_id),
    KEY idx_citas_contribuyente_inicio (contribuyente_id, inicio),
    KEY idx_citas_inicio (inicio),
    KEY idx_citas_usuario (registrado_por),
    CONSTRAINT fk_citas_contribuyente FOREIGN KEY (contribuyente_id) REFERENCES contribuyentes (id),
    CONSTRAINT fk_citas_habilitacion FOREIGN KEY (ventanilla_id, funcionario_id)
        REFERENCES ventanillas_funcionarios (ventanilla_id, funcionario_id),
    CONSTRAINT fk_citas_usuario FOREIGN KEY (registrado_por) REFERENCES usuarios (id),
    CONSTRAINT ck_citas_intervalo CHECK (fin > inicio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Mercado. La ocupación se deriva de la asignación vigente.
CREATE TABLE mercados (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(35) NOT NULL,
    nombre VARCHAR(140) NOT NULL,
    baja_en DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_mercados_codigo (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sectores_mercado (
    id SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(35) NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_sectores_mercado_codigo (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE puestos (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    mercado_id INT UNSIGNED NOT NULL,
    sector_id SMALLINT UNSIGNED NOT NULL,
    numero VARCHAR(30) NOT NULL,
    baja_en DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_puestos_mercado_sector_numero (mercado_id, sector_id, numero),
    KEY idx_puestos_sector (sector_id),
    CONSTRAINT fk_puestos_mercado FOREIGN KEY (mercado_id) REFERENCES mercados (id),
    CONSTRAINT fk_puestos_sector FOREIGN KEY (sector_id) REFERENCES sectores_mercado (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE asignaciones_puestos (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    puesto_id BIGINT UNSIGNED NOT NULL,
    comerciante_id BIGINT UNSIGNED NOT NULL,
    giro_comercial VARCHAR(140) NOT NULL,
    asignado_por BIGINT UNSIGNED NOT NULL,
    asignado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    liberado_por BIGINT UNSIGNED NULL,
    liberado_en DATETIME(6) NULL,
    puesto_vigente_id BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN liberado_en IS NULL THEN puesto_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_asignaciones_puesto_vigente (puesto_vigente_id),
    KEY idx_asignaciones_puesto_fecha (puesto_id, asignado_en),
    KEY idx_asignaciones_comerciante (comerciante_id, asignado_en),
    KEY idx_asignaciones_asignado_por (asignado_por),
    KEY idx_asignaciones_liberado_por (liberado_por),
    CONSTRAINT fk_asignaciones_puesto FOREIGN KEY (puesto_id) REFERENCES puestos (id),
    CONSTRAINT fk_asignaciones_comerciante FOREIGN KEY (comerciante_id) REFERENCES contribuyentes (id),
    CONSTRAINT fk_asignaciones_asignado_por FOREIGN KEY (asignado_por) REFERENCES usuarios (id),
    CONSTRAINT fk_asignaciones_liberado_por FOREIGN KEY (liberado_por) REFERENCES usuarios (id),
    CONSTRAINT ck_asignaciones_liberacion CHECK (
        (liberado_en IS NULL AND liberado_por IS NULL)
        OR (liberado_en IS NOT NULL AND liberado_por IS NOT NULL AND liberado_en > asignado_en)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Almacén. El saldo por material se obtiene sumando los saldos de sus lotes.
CREATE TABLE materiales (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(40) NOT NULL,
    nombre VARCHAR(140) NOT NULL,
    unidad_medida VARCHAR(35) NOT NULL,
    stock_minimo DECIMAL(14,3) NOT NULL DEFAULT 0,
    baja_en DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_materiales_codigo (codigo),
    KEY idx_materiales_activos_nombre (baja_en, nombre),
    CONSTRAINT ck_materiales_stock_minimo CHECK (stock_minimo >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lotes_material (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    material_id BIGINT UNSIGNED NOT NULL,
    codigo_lote VARCHAR(40) NOT NULL,
    vence_en DATE NULL,
    stock DECIMAL(14,3) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_lotes_material_codigo (material_id, codigo_lote),
    KEY idx_lotes_vencimiento (vence_en, stock),
    CONSTRAINT fk_lotes_material FOREIGN KEY (material_id) REFERENCES materiales (id),
    CONSTRAINT ck_lotes_stock CHECK (stock >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE movimientos_stock (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo_movimiento VARCHAR(40) NOT NULL,
    lote_id BIGINT UNSIGNED NOT NULL,
    tipo ENUM('INGRESO', 'SALIDA') NOT NULL,
    cantidad DECIMAL(14,3) NOT NULL,
    area_solicitante_id SMALLINT UNSIGNED NULL,
    registrado_por BIGINT UNSIGNED NOT NULL,
    motivo VARCHAR(250) NOT NULL,
    registrado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_movimientos_stock_codigo (codigo_movimiento),
    KEY idx_movimientos_stock_lote_fecha (lote_id, registrado_en),
    KEY idx_movimientos_stock_area_fecha (area_solicitante_id, registrado_en),
    KEY idx_movimientos_stock_usuario (registrado_por),
    CONSTRAINT fk_movimientos_stock_lote FOREIGN KEY (lote_id) REFERENCES lotes_material (id),
    CONSTRAINT fk_movimientos_stock_area FOREIGN KEY (area_solicitante_id) REFERENCES areas (id),
    CONSTRAINT fk_movimientos_stock_usuario FOREIGN KEY (registrado_por) REFERENCES usuarios (id),
    CONSTRAINT ck_movimientos_stock_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_movimientos_stock_tipo CHECK (tipo IN ('INGRESO', 'SALIDA')),
    CONSTRAINT ck_movimientos_stock_motivo CHECK (CHAR_LENGTH(TRIM(motivo)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER $$

-- Las dos escrituras de agenda comparten la misma validación. Los bloqueos
-- por ventanilla y por funcionario se mantienen hasta finalizar la transacción.
CREATE PROCEDURE validar_reserva_cita (
    IN p_cita_id BIGINT UNSIGNED,
    IN p_ventanilla_id INT UNSIGNED,
    IN p_funcionario_id BIGINT UNSIGNED,
    IN p_inicio DATETIME(6),
    IN p_fin DATETIME(6)
)
READS SQL DATA
BEGIN
    DECLARE v_area_id SMALLINT UNSIGNED;
    DECLARE v_funcionario_id BIGINT UNSIGNED;

    SELECT (
        SELECT v.area_id
        FROM ventanillas AS v
        WHERE v.id = p_ventanilla_id AND v.baja_en IS NULL
        FOR UPDATE
    ) INTO v_area_id;

    SELECT (
        SELECT u.id
        FROM usuarios AS u
        WHERE u.id = p_funcionario_id
        FOR UPDATE
    ) INTO v_funcionario_id;

    IF v_area_id IS NULL OR v_funcionario_id IS NULL OR NOT EXISTS (
        SELECT 1
        FROM ventanillas_funcionarios AS vf
        JOIN areas AS a ON a.id = v_area_id
        WHERE vf.ventanilla_id = p_ventanilla_id
          AND vf.funcionario_id = v_funcionario_id
          AND vf.baja_en IS NULL AND a.baja_en IS NULL
        FOR SHARE
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La ventanilla, el área o el funcionario no está habilitado.';
    END IF;

    -- FOR SHARE lee la reserva más reciente aun si la transacción ya tiene un snapshot.
    -- Los intervalos consecutivos son válidos y el ID previo se excluye al editar.
    IF EXISTS (
        SELECT 1 FROM citas AS c
        WHERE c.funcionario_id = p_funcionario_id
          AND c.inicio_reservado < p_fin AND c.fin > p_inicio
          AND (p_cita_id IS NULL OR c.id <> p_cita_id)
        FOR SHARE
    ) OR EXISTS (
        SELECT 1 FROM citas AS c
        WHERE c.ventanilla_id = p_ventanilla_id
          AND c.inicio_reservado < p_fin AND c.fin > p_inicio
          AND (p_cita_id IS NULL OR c.id <> p_cita_id)
        FOR SHARE
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La cita se solapa con otra reserva del funcionario o de la ventanilla.';
    END IF;
END$$

CREATE TRIGGER trg_citas_validar_alta
BEFORE INSERT ON citas
FOR EACH ROW
BEGIN
    IF NEW.estado <> 'CANCELADA' THEN
        CALL validar_reserva_cita(NULL, NEW.ventanilla_id, NEW.funcionario_id, NEW.inicio, NEW.fin);
    END IF;
END$$

CREATE TRIGGER trg_citas_validar_cambio
BEFORE UPDATE ON citas
FOR EACH ROW
BEGIN
    IF NEW.estado <> 'CANCELADA' AND (
        OLD.estado = 'CANCELADA'
        OR NEW.ventanilla_id <> OLD.ventanilla_id
        OR NEW.funcionario_id <> OLD.funcionario_id
        OR NEW.inicio <> OLD.inicio
        OR NEW.fin <> OLD.fin
    ) THEN
        CALL validar_reserva_cita(OLD.id, NEW.ventanilla_id, NEW.funcionario_id, NEW.inicio, NEW.fin);
    END IF;
END$$

CREATE TRIGGER trg_lotes_stock_inicial
BEFORE INSERT ON lotes_material
FOR EACH ROW
BEGIN
    IF NEW.stock <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El saldo inicial se registra como un ingreso.';
    END IF;
END$$

CREATE TRIGGER trg_movimientos_aplicar_stock
AFTER INSERT ON movimientos_stock
FOR EACH ROW
BEGIN
    -- La condición se evalúa sobre la fila bloqueada por el UPDATE.
    UPDATE lotes_material AS l
    JOIN materiales AS m ON m.id = l.material_id
    SET l.stock = CASE NEW.tipo
        WHEN 'INGRESO' THEN l.stock + NEW.cantidad
        ELSE l.stock - NEW.cantidad
    END
    WHERE l.id = NEW.lote_id
      AND m.baja_en IS NULL
      -- 99999999999.999 es el máximo representable por DECIMAL(14,3).
      AND (
          (NEW.tipo = 'INGRESO' AND l.stock <= 99999999999.999 - NEW.cantidad)
          OR (NEW.tipo = 'SALIDA' AND l.stock >= NEW.cantidad)
      );

    IF ROW_COUNT() <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Material de baja o saldo del lote fuera de los límites.';
    END IF;
END$$

CREATE TRIGGER trg_movimientos_impedir_actualizacion
BEFORE UPDATE ON movimientos_stock
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registre un movimiento de compensación.';
END$$

CREATE TRIGGER trg_movimientos_impedir_borrado
BEFORE DELETE ON movimientos_stock
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Los movimientos de almacén conservan su historial.';
END$$

DELIMITER ;
