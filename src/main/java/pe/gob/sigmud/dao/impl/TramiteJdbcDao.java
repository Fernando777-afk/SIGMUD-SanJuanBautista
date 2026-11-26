package pe.gob.sigmud.dao.impl;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import pe.gob.sigmud.dao.AlcanceAreas;
import pe.gob.sigmud.dao.Jdbc;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla.Opcion;
import pe.gob.sigmud.model.Tramite;
import pe.gob.sigmud.model.Tramite.Estado;
import pe.gob.sigmud.model.Tramite.Movimiento;
import pe.gob.sigmud.util.ConexionBD;

public final class TramiteJdbcDao {
    private static final String EXPEDIENTE = """
            SELECT t.id, t.numero_expediente, t.contribuyente_id, c.nombre_razon_social, t.tipo_tramite,
                   t.descripcion, t.registrado_en, h.id AS version, a.id AS area_id, a.nombre AS area, h.estado
            FROM tramites t JOIN contribuyentes c ON c.id = t.contribuyente_id
            JOIN historial_tramites h ON h.id = (
                SELECT MAX(hu.id) FROM historial_tramites hu WHERE hu.tramite_id = t.id
            )
            JOIN areas a ON a.id = h.area_id
            """;
    private final Jdbc.Conexion fuente;

    public record Filtros(String texto, Long contribuyente, Long area, Estado estado, LocalDate desde, LocalDate hasta) {}
    public record Detalle(Tramite tramite, List<Movimiento> movimientos) {}

    public TramiteJdbcDao() { this(ConexionBD::abrir); }
    public TramiteJdbcDao(Jdbc.Conexion fuente) { this.fuente = fuente; }

    public Pagina<Tramite> listar(Identidad usuario, Filtros filtros, int pagina) throws SQLException {
        String estado = filtros.estado() == null ? null : filtros.estado().name();
        Object[] parametros = {Jdbc.contiene(filtros.texto()), filtros.contribuyente(), filtros.contribuyente(),
            filtros.area(), filtros.area(), estado, estado, filtros.desde(), filtros.desde(), filtros.hasta(), filtros.hasta()};
        String consulta = EXPEDIENTE + """
                WHERE t.numero_expediente LIKE ? ESCAPE '!' AND (? IS NULL OR t.contribuyente_id = ?)
                    AND (? IS NULL OR h.area_id = ?) AND (? IS NULL OR h.estado = ?)
                    AND (? IS NULL OR t.registrado_en >= ?) AND (? IS NULL OR t.registrado_en < ?)
                    AND
                """ + AlcanceAreas.condicion("h.area_id") + " ORDER BY t.registrado_en DESC, t.id DESC";
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.pagina(conexion, consulta, TramiteJdbcDao::fila, pagina,
                    Jdbc.unir(parametros, AlcanceAreas.parametros(usuario)));
        }
    }

    public Optional<Detalle> detalle(Identidad usuario, long id) throws SQLException {
        return Jdbc.transaccion(fuente, conexion -> {
            var tramite = Jdbc.listar(conexion, EXPEDIENTE + " WHERE t.id = ? AND "
                    + AlcanceAreas.condicion("h.area_id"), TramiteJdbcDao::fila,
                    Jdbc.unir(new Object[] {id}, AlcanceAreas.parametros(usuario))).stream().findFirst();
            if (tramite.isEmpty()) return Optional.empty();
            return Optional.of(new Detalle(tramite.get(), historial(conexion, id)));
        });
    }

    public List<Detalle> historialContribuyente(Identidad usuario, long id) throws SQLException {
        return Jdbc.transaccion(fuente, conexion -> {
            var expedientes = Jdbc.listar(conexion, EXPEDIENTE + " WHERE t.contribuyente_id = ? AND "
                    + AlcanceAreas.condicion("h.area_id") + " ORDER BY t.registrado_en, t.id",
                    TramiteJdbcDao::fila, Jdbc.unir(new Object[] {id}, AlcanceAreas.parametros(usuario)));
            var resultado = new java.util.ArrayList<Detalle>();
            for (Tramite expediente : expedientes) resultado.add(new Detalle(expediente, historial(conexion, expediente.id())));
            return resultado;
        });
    }

    public long registrar(long actor, String codigo, long contribuyente, long area, String tipo, String descripcion) throws SQLException {
        return Jdbc.transaccion(fuente, conexion -> {
            ContribuyenteJdbcDao.exigirVigente(conexion, contribuyente);
            exigirAreaVigente(conexion, area);
            long id = Jdbc.insertar(conexion, """
                    INSERT INTO tramites (numero_expediente, contribuyente_id, registrado_por, tipo_tramite, descripcion)
                    VALUES (?, ?, ?, ?, ?)
                    """, codigo, contribuyente, actor, tipo, descripcion);
            registrarMovimiento(conexion, id, actor, area, Estado.RECIBIDO, null);
            return id;
        });
    }

    public void actualizar(Identidad usuario, long id, long version, long area, Estado estado, String observacion) throws SQLException {
        Jdbc.transaccion(fuente, conexion -> {
            if (Jdbc.listar(conexion, "SELECT id FROM tramites WHERE id = ? FOR UPDATE", f -> f.getLong(1), id).isEmpty()) {
                throw new IllegalArgumentException("El expediente no está disponible.");
            }
            Tramite actual = Jdbc.listar(conexion, EXPEDIENTE + " WHERE t.id = ? AND "
                    + AlcanceAreas.condicion("h.area_id"), TramiteJdbcDao::fila,
                    Jdbc.unir(new Object[] {id}, AlcanceAreas.parametros(usuario))).stream().findFirst().orElseThrow(
                    () -> new SecurityException("No tienes acceso a este expediente."));
            if (actual.version() != version) {
                throw new IllegalArgumentException("El expediente cambió. Revisa el último movimiento antes de continuar.");
            }
            if (actual.estado() == Estado.ARCHIVADO) {
                throw new IllegalArgumentException("El expediente ya está archivado.");
            }
            if (area == actual.areaId() && estado == actual.estado() && observacion == null) {
                throw new IllegalArgumentException("Indica un cambio de área, estado u observación.");
            }
            exigirAreaVigente(conexion, area);
            registrarMovimiento(conexion, id, usuario.id(), area, estado, observacion);
            return null;
        });
    }

    public List<Opcion> areas(Identidad usuario, boolean autorizadas) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            return AlcanceAreas.areas(conexion, usuario, autorizadas);
        }
    }

    public List<Opcion> contribuyentes(String texto, Long seleccionado) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            return ContribuyenteJdbcDao.opciones(conexion, texto, seleccionado);
        }
    }

    public List<Opcion> tipos(Identidad usuario) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.listar(conexion, """
                    SELECT DISTINCT t.tipo_tramite FROM tramites t JOIN historial_tramites h ON h.id = (
                        SELECT MAX(x.id) FROM historial_tramites x WHERE x.tramite_id = t.id
                    ) WHERE
                    """ + AlcanceAreas.condicion("h.area_id") + " ORDER BY t.tipo_tramite LIMIT 80",
                    f -> new Opcion(f.getString(1), f.getString(1)), AlcanceAreas.parametros(usuario));
        }
    }

    public static void exigirAreaVigente(Connection conexion, long area) throws SQLException {
        if (Jdbc.listar(conexion, "SELECT id FROM areas WHERE id = ? AND baja_en IS NULL", f -> f.getLong(1), area).isEmpty()) {
            throw new IllegalArgumentException("Selecciona un área municipal vigente.");
        }
    }

    private static void registrarMovimiento(Connection conexion, long id, long actor, long area,
            Estado estado, String observacion) throws SQLException {
        Jdbc.actualizar(conexion, """
                INSERT INTO historial_tramites (tramite_id, area_id, estado, registrado_por, observacion)
                VALUES (?, ?, ?, ?, ?)
                """, id, area, estado.name(), actor, observacion);
    }

    private static List<Movimiento> historial(Connection conexion, long id) throws SQLException {
        return Jdbc.listar(conexion, """
                SELECT h.id, a.nombre AS area, h.estado, u.nombre_visible, h.registrado_en, h.observacion
                FROM historial_tramites h JOIN areas a ON a.id = h.area_id JOIN usuarios u ON u.id = h.registrado_por
                WHERE h.tramite_id = ? ORDER BY h.id DESC
                """, f -> new Movimiento(f.getLong("id"), f.getString("area"), Estado.valueOf(f.getString("estado")),
                    f.getString("nombre_visible"), f.getObject("registrado_en", LocalDateTime.class),
                    f.getString("observacion")), id);
    }

    private static Tramite fila(ResultSet f) throws SQLException {
        return new Tramite(f.getLong("id"), f.getString("numero_expediente"), f.getLong("contribuyente_id"),
                f.getString("nombre_razon_social"), f.getString("tipo_tramite"), f.getString("descripcion"),
                f.getObject("registrado_en", LocalDateTime.class), f.getLong("version"), f.getLong("area_id"),
                f.getString("area"), Estado.valueOf(f.getString("estado")));
    }
}
