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
import pe.gob.sigmud.model.CitaMunicipal;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla.Opcion;
import pe.gob.sigmud.util.ConexionBD;

public final class CitaJdbcDao {
    private static final String SELECT = """
            SELECT c.*, p.nombre_razon_social AS titular, v.nombre AS ventanilla, u.nombre_visible AS funcionario, a.nombre AS area
            FROM citas c JOIN ventanillas v ON v.id = c.ventanilla_id JOIN areas a ON a.id = v.area_id
            JOIN usuarios u ON u.id = c.funcionario_id JOIN contribuyentes p ON p.id = c.contribuyente_id
            """;
    private final Jdbc.Conexion fuente;

    public record Filtro(String codigo, String estado, Long area, Long funcionario, LocalDate desde, LocalDate hasta) {}
    public record Horario(long ventanilla, long funcionario, LocalDateTime inicio, LocalDateTime fin) {}
    public record Alta(String codigo, long persona, Horario horario, String motivo) {}

    public CitaJdbcDao() { this(ConexionBD::abrir); }
    public CitaJdbcDao(Jdbc.Conexion fuente) { this.fuente = fuente; }

    public Pagina<CitaMunicipal> agenda(Identidad usuario, Filtro filtro, int pagina) throws SQLException {
        String sql = SELECT + """
                WHERE c.codigo_cita LIKE ? ESCAPE '!' AND (? IS NULL OR c.estado = ?)
                  AND (? IS NULL OR v.area_id = ?) AND (? IS NULL OR c.funcionario_id = ?)
                  AND (? IS NULL OR c.inicio >= ?) AND (? IS NULL OR c.inicio < ?) AND
                """ + AlcanceAreas.condicion("v.area_id") + " ORDER BY c.inicio, c.id";
        Object[] base = {Jdbc.contiene(filtro.codigo()), filtro.estado(), filtro.estado(), filtro.area(), filtro.area(),
            filtro.funcionario(), filtro.funcionario(), filtro.desde(), filtro.desde(), filtro.hasta(), filtro.hasta()};
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.pagina(conexion, sql, CitaJdbcDao::fila, pagina, Jdbc.unir(base, AlcanceAreas.parametros(usuario)));
        }
    }

    public Optional<CitaMunicipal> detalle(Identidad usuario, long id) throws SQLException {
        try (Connection conexion = fuente.abrir()) { return buscar(conexion, usuario, id); }
    }

    public List<Opcion> areas(Identidad usuario) throws SQLException {
        try (Connection conexion = fuente.abrir()) { return AlcanceAreas.areas(conexion, usuario, true); }
    }

    public List<Opcion> ventanillas(Identidad usuario) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.listar(conexion, """
                    SELECT v.id, v.nombre, a.nombre AS area FROM ventanillas v JOIN areas a ON a.id = v.area_id
                    WHERE v.baja_en IS NULL AND a.baja_en IS NULL AND
                    """ + AlcanceAreas.condicion("v.area_id") + " ORDER BY a.nombre, v.nombre",
                    f -> new Opcion(f.getLong("id"), f.getString("area") + " · " + f.getString("nombre")),
                    AlcanceAreas.parametros(usuario));
        }
    }

    public List<Opcion> funcionarios(Identidad usuario, Long ventanilla, boolean activos) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.listar(conexion, """
                    SELECT DISTINCT u.id, u.nombre_visible FROM usuarios u JOIN ventanillas_funcionarios vf ON vf.funcionario_id = u.id
                    JOIN ventanillas v ON v.id = vf.ventanilla_id JOIN areas a ON a.id = v.area_id
                    WHERE (? = FALSE OR (u.activo = 1 AND a.baja_en IS NULL)) AND vf.baja_en IS NULL AND v.baja_en IS NULL AND (? IS NULL OR v.id = ?) AND
                    """ + AlcanceAreas.condicion("v.area_id") + " ORDER BY u.nombre_visible, u.id",
                    f -> new Opcion(f.getLong("id"), f.getString("nombre_visible")),
                    Jdbc.unir(new Object[] {activos, ventanilla, ventanilla}, AlcanceAreas.parametros(usuario)));
        }
    }

    public List<Opcion> contribuyentes(String texto) throws SQLException {
        try (Connection conexion = fuente.abrir()) { return ContribuyenteJdbcDao.opciones(conexion, texto, null); }
    }

    public boolean disponible(Identidad usuario, Horario horario) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            exigirVentanilla(conexion, usuario, horario);
            return Jdbc.listar(conexion, """
                    SELECT 1 FROM citas c WHERE c.inicio_reservado < ? AND c.fin > ?
                    AND (c.ventanilla_id = ? OR c.funcionario_id = ?) LIMIT 1
                    """, f -> f.getInt(1), horario.fin(), horario.inicio(), horario.ventanilla(), horario.funcionario()).isEmpty();
        }
    }

    public long guardar(Identidad usuario, Long sustituida, Alta alta) throws SQLException {
        return Jdbc.transaccion(fuente, conexion -> {
            long persona = alta.persona();
            if (sustituida != null) {
                CitaMunicipal anterior = bloquear(conexion, usuario, sustituida);
                persona = anterior.contribuyente();
                Jdbc.actualizar(conexion, "UPDATE citas SET estado = 'CANCELADA' WHERE id = ?", sustituida);
            }
            ContribuyenteJdbcDao.exigirVigente(conexion, persona);
            Horario h = alta.horario();
            exigirVentanilla(conexion, usuario, h);
            return Jdbc.insertar(conexion, """
                    INSERT INTO citas (codigo_cita, contribuyente_id, ventanilla_id, funcionario_id,
                        registrado_por, inicio, fin, motivo, estado, registrado_en)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PROGRAMADA', CURRENT_TIMESTAMP(6))
                    """, alta.codigo(), persona, h.ventanilla(), h.funcionario(), usuario.id(), h.inicio(),
                    h.fin(), alta.motivo());
        });
    }

    public void finalizar(Identidad usuario, long id, String estado) throws SQLException {
        Jdbc.transaccion(fuente, conexion -> {
            CitaMunicipal cita = bloquear(conexion, usuario, id);
            if (!"CANCELADA".equals(estado) && cita.inicio().isAfter(LocalDateTime.now(java.time.ZoneId.of("America/Lima")))) {
                throw new IllegalArgumentException("La cita aún no ha comenzado.");
            }
            Jdbc.actualizar(conexion, "UPDATE citas SET estado = ? WHERE id = ?", estado, id);
            return null;
        });
    }

    private static void exigirVentanilla(Connection conexion, Identidad usuario, Horario horario) throws SQLException {
        var areas = Jdbc.listar(conexion, """
                SELECT v.area_id FROM ventanillas v JOIN ventanillas_funcionarios vf ON vf.ventanilla_id = v.id
                JOIN usuarios u ON u.id = vf.funcionario_id
                WHERE v.id = ? AND vf.funcionario_id = ? AND u.activo = 1 AND v.baja_en IS NULL AND vf.baja_en IS NULL
                """, f -> f.getLong(1), horario.ventanilla(), horario.funcionario());
        if (areas.isEmpty()) throw new IllegalArgumentException("El funcionario no está habilitado en esa ventanilla.");
        TramiteJdbcDao.exigirAreaVigente(conexion, areas.get(0));
        if (!AlcanceAreas.permite(conexion, usuario, areas.get(0))) throw new SecurityException("Ventanilla fuera de tu área.");
    }

    private static Optional<CitaMunicipal> buscar(Connection conexion, Identidad usuario, long id) throws SQLException {
        return Jdbc.listar(conexion, SELECT + " WHERE c.id = ? AND " + AlcanceAreas.condicion("v.area_id"),
                CitaJdbcDao::fila, Jdbc.unir(new Object[] {id}, AlcanceAreas.parametros(usuario))).stream().findFirst();
    }

    private static CitaMunicipal bloquear(Connection conexion, Identidad usuario, long id) throws SQLException {
        Jdbc.listar(conexion, "SELECT id FROM citas WHERE id = ? FOR UPDATE", f -> f.getLong(1), id);
        CitaMunicipal cita = buscar(conexion, usuario, id).orElseThrow(() -> new SecurityException("Cita no autorizada."));
        if (!cita.programada()) throw new IllegalArgumentException("Solo se puede modificar una cita programada.");
        return cita;
    }

    private static CitaMunicipal fila(ResultSet f) throws SQLException {
        return new CitaMunicipal(f.getLong("id"), f.getString("codigo_cita"), f.getLong("contribuyente_id"),
                f.getString("titular"), f.getLong("ventanilla_id"), f.getString("ventanilla"), f.getLong("funcionario_id"),
                f.getString("funcionario"), f.getString("area"), f.getObject("inicio", LocalDateTime.class),
                f.getObject("fin", LocalDateTime.class), f.getString("motivo"), f.getString("estado"));
    }
}
