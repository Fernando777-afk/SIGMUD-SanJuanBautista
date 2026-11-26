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
import pe.gob.sigmud.model.CasoMunicipal;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla.Opcion;
import pe.gob.sigmud.util.ConexionBD;

public final class CasoJdbcDao {
    private static final String SELECT = """
            SELECT r.*, a.nombre AS area, c.nombre_razon_social AS titular
            FROM reclamos_emergencias r JOIN areas a ON a.id = r.area_id
            LEFT JOIN contribuyentes c ON c.id = r.contribuyente_id
            """;
    private final Jdbc.Conexion fuente;

    public record Filtro(String codigo, String tipo, String prioridad, String estado, Long area, LocalDate desde, LocalDate hasta) {}
    public record Alta(String codigo, String tipo, String prioridad, long area, Long persona, String descripcion, String ubicacion) {}

    public CasoJdbcDao() { this(ConexionBD::abrir); }
    public CasoJdbcDao(Jdbc.Conexion fuente) { this.fuente = fuente; }

    public Pagina<CasoMunicipal> listar(Identidad usuario, Filtro f, int pagina) throws SQLException {
        String sql = SELECT + """
                WHERE r.codigo_caso LIKE ? ESCAPE '!' AND (? IS NULL OR r.tipo = ?)
                  AND (? IS NULL OR r.prioridad = ? OR (? = 'URGENTE' AND r.prioridad IN ('ALTA','CRITICA')))
                  AND (? IS NULL OR r.area_id = ?)
                  AND (? IS NULL OR r.registrado_en >= ?) AND (? IS NULL OR r.registrado_en < ?) AND
                """ + ("ABIERTOS".equals(f.estado()) ? "r.estado IN ('PENDIENTE','EN_ATENCION')"
                        : "(? IS NULL OR r.estado = ?)")
                + " AND " + AlcanceAreas.condicion("r.area_id") + """
                  ORDER BY CASE WHEN r.estado IN ('PENDIENTE','EN_ATENCION') THEN 0 ELSE 1 END,
                           r.prioridad DESC, r.registrado_en, r.id
                """;
        Object[] base = {Jdbc.contiene(f.codigo()), f.tipo(), f.tipo(), f.prioridad(), f.prioridad(), f.prioridad(), f.area(),
            f.area(), f.desde(), f.desde(), f.hasta(), f.hasta()};
        if (!"ABIERTOS".equals(f.estado())) base = Jdbc.unir(base, f.estado(), f.estado());
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.pagina(conexion, sql, CasoJdbcDao::fila, pagina, Jdbc.unir(base, AlcanceAreas.parametros(usuario)));
        }
    }

    public Optional<CasoMunicipal> buscar(Identidad usuario, long id) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            return buscar(conexion, usuario, id);
        }
    }

    public long registrar(Identidad usuario, Alta alta) throws SQLException {
        return Jdbc.transaccion(fuente, conexion -> {
            exigirArea(conexion, usuario, alta.area());
            if (alta.persona() != null) ContribuyenteJdbcDao.exigirVigente(conexion, alta.persona());
            return Jdbc.insertar(conexion, """
                    INSERT INTO reclamos_emergencias (codigo_caso, tipo, contribuyente_id, area_id, registrado_por,
                        descripcion, referencia_ubicacion, prioridad) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, alta.codigo(), alta.tipo(), alta.persona(), alta.area(), usuario.id(), alta.descripcion(),
                    alta.ubicacion(), alta.prioridad());
        });
    }

    public void atender(Identidad usuario, long id, long area, String estado, String prioridad,
            long areaAnterior, String estadoAnterior, String prioridadAnterior) throws SQLException {
        Jdbc.transaccion(fuente, conexion -> {
            Jdbc.listar(conexion, "SELECT id FROM reclamos_emergencias WHERE id = ? FOR UPDATE", f -> f.getLong(1), id);
            CasoMunicipal actual = buscar(conexion, usuario, id).orElseThrow(() -> new SecurityException("Caso fuera de tu área."));
            if (!actual.abierto() || actual.areaId() != areaAnterior || !actual.estado().equals(estadoAnterior)
                    || !actual.prioridad().equals(prioridadAnterior)) {
                throw new IllegalArgumentException("El estado o la asignación cambió. Consulta de nuevo el caso.");
            }
            exigirArea(conexion, usuario, area);
            Jdbc.actualizar(conexion, """
                    UPDATE reclamos_emergencias SET area_id = ?, estado = ?, prioridad = ?,
                    atendido_en = CASE WHEN ? = 'ATENDIDO' THEN GREATEST(CURRENT_TIMESTAMP(6), registrado_en) ELSE NULL END
                    WHERE id = ?
                    """, area, estado, prioridad, estado, id);
            return null;
        });
    }

    public List<Opcion> areas(Identidad usuario) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            return AlcanceAreas.areas(conexion, usuario, true);
        }
    }

    public List<Opcion> contribuyentes(String texto) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            return ContribuyenteJdbcDao.opciones(conexion, texto, null);
        }
    }

    private static Optional<CasoMunicipal> buscar(Connection conexion, Identidad usuario, long id) throws SQLException {
        return Jdbc.listar(conexion, SELECT + " WHERE r.id = ? AND " + AlcanceAreas.condicion("r.area_id"), CasoJdbcDao::fila,
                Jdbc.unir(new Object[] {id}, AlcanceAreas.parametros(usuario))).stream().findFirst();
    }

    private static void exigirArea(Connection conexion, Identidad usuario, long area) throws SQLException {
        TramiteJdbcDao.exigirAreaVigente(conexion, area);
        if (!AlcanceAreas.permite(conexion, usuario, area)) throw new SecurityException("Área no autorizada.");
    }

    private static CasoMunicipal fila(ResultSet f) throws SQLException {
        return new CasoMunicipal(f.getLong("id"), f.getString("codigo_caso"), f.getString("tipo"), f.getString("prioridad"),
                f.getString("estado"), f.getLong("area_id"), f.getString("area"), f.getObject("contribuyente_id", Long.class),
                f.getString("titular"), f.getString("descripcion"), f.getString("referencia_ubicacion"),
                f.getObject("registrado_en", LocalDateTime.class), f.getObject("atendido_en", LocalDateTime.class));
    }
}
