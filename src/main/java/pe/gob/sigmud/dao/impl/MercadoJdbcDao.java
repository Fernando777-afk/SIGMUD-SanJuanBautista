package pe.gob.sigmud.dao.impl;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import pe.gob.sigmud.dao.Jdbc;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla.Opcion;
import pe.gob.sigmud.util.ConexionBD;

public final class MercadoJdbcDao {
    private static final String PUESTO = """
            SELECT p.id, p.numero, m.nombre AS mercado, s.nombre AS sector, ag.id AS asignacion,
                   c.nombre_razon_social AS comerciante
            FROM puestos p JOIN mercados m ON m.id = p.mercado_id JOIN sectores_mercado s ON s.id = p.sector_id
            LEFT JOIN asignaciones_puestos ag ON ag.puesto_vigente_id = p.id
            LEFT JOIN contribuyentes c ON c.id = ag.comerciante_id
            WHERE p.baja_en IS NULL AND m.baja_en IS NULL
            """;
    private final Jdbc.Conexion fuente;

    public enum Catalogo {
        MERCADOS("mercados", " WHERE baja_en IS NULL"), SECTORES("sectores_mercado", "");
        private final String tabla;
        private final String vigentes;
        Catalogo(String tabla, String vigentes) { this.tabla = tabla; this.vigentes = vigentes; }
    }
    public record Entrada(long id, String codigo, String nombre) {}
    public record Puesto(long id, String numero, String mercado, String sector, Long asignacion, String comerciante) {
        public boolean ocupado() { return asignacion != null; }
    }
    public record Asignacion(String comerciante, String giro, LocalDateTime asignado, LocalDateTime liberado) {}
    public record Ocupacion(String mercado, long puestos, long ocupados) {}

    public MercadoJdbcDao() { this(ConexionBD::abrir); }
    public MercadoJdbcDao(Jdbc.Conexion fuente) { this.fuente = fuente; }

    public Pagina<Puesto> puestos(String numero, Long mercado, Long sector, String estado, int pagina) throws SQLException {
        String estadoSql = switch (estado) {
            case "LIBRE" -> " AND ag.id IS NULL";
            case "OCUPADO" -> " AND ag.id IS NOT NULL";
            default -> "";
        };
        String sql = PUESTO + """
                AND p.numero LIKE ? ESCAPE '!' AND (? IS NULL OR m.id = ?) AND (? IS NULL OR s.id = ?)
                """ + estadoSql + " ORDER BY m.nombre, s.nombre, p.numero, p.id";
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.pagina(conexion, sql, MercadoJdbcDao::fila, pagina, Jdbc.contiene(numero),
                    mercado, mercado, sector, sector);
        }
    }

    public Optional<Puesto> puesto(long id) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.listar(conexion, PUESTO + " AND p.id = ?", MercadoJdbcDao::fila, id).stream().findFirst();
        }
    }

    public Pagina<Asignacion> historial(long id, int pagina) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.pagina(conexion, """
                    SELECT c.nombre_razon_social, ap.giro_comercial, ap.asignado_en, ap.liberado_en
                    FROM asignaciones_puestos ap JOIN contribuyentes c ON c.id = ap.comerciante_id
                    WHERE ap.puesto_id = ? ORDER BY ap.asignado_en DESC, ap.id DESC
                    """, f -> new Asignacion(f.getString(1), f.getString(2), f.getObject(3, LocalDateTime.class),
                        f.getObject(4, LocalDateTime.class)), pagina, id);
        }
    }

    public List<Ocupacion> ocupacion() throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.listar(conexion, """
                    SELECT m.nombre, COUNT(p.id) AS puestos, COUNT(a.id) AS ocupados
                    FROM mercados m LEFT JOIN puestos p ON p.mercado_id = m.id AND p.baja_en IS NULL
                    LEFT JOIN asignaciones_puestos a ON a.puesto_vigente_id = p.id
                    WHERE m.baja_en IS NULL GROUP BY m.id, m.nombre ORDER BY m.nombre
                    """, f -> new Ocupacion(f.getString(1), f.getLong(2), f.getLong(3)));
        }
    }

    public List<Entrada> catalogo(Catalogo catalogo) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.listar(conexion, "SELECT id, codigo, nombre FROM " + catalogo.tabla
                    + catalogo.vigentes + " ORDER BY nombre, id", f -> new Entrada(f.getLong(1), f.getString(2), f.getString(3)));
        }
    }

    public List<Opcion> contribuyentes(String texto) throws SQLException {
        try (Connection conexion = fuente.abrir()) { return ContribuyenteJdbcDao.opciones(conexion, texto, null); }
    }

    public void guardarCatalogo(Catalogo catalogo, Long id, String codigo, String nombre) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            if (id == null) {
                Jdbc.actualizar(conexion, "INSERT INTO " + catalogo.tabla + " (codigo, nombre) VALUES (?, ?)", codigo, nombre);
            } else if (Jdbc.actualizar(conexion, "UPDATE " + catalogo.tabla + " SET nombre = ? WHERE id = ?"
                    + (catalogo == Catalogo.MERCADOS ? " AND baja_en IS NULL" : ""), nombre, id) == 0) {
                throw new IllegalArgumentException("El registro ya no está disponible.");
            }
        }
    }

    public long altaPuesto(long mercado, long sector, String numero) throws SQLException {
        return Jdbc.transaccion(fuente, conexion -> {
            if (Jdbc.listar(conexion, "SELECT id FROM mercados WHERE id = ? AND baja_en IS NULL FOR UPDATE",
                    f -> f.getLong(1), mercado).isEmpty()) throw new IllegalArgumentException("Selecciona un mercado vigente.");
            if (Jdbc.listar(conexion, "SELECT id FROM sectores_mercado WHERE id = ?", f -> f.getLong(1), sector).isEmpty()) {
                throw new IllegalArgumentException("Selecciona un sector registrado.");
            }
            return Jdbc.insertar(conexion, "INSERT INTO puestos (mercado_id, sector_id, numero) VALUES (?, ?, ?)", mercado, sector, numero);
        });
    }

    public void asignar(long puesto, long persona, String giro, long usuario) throws SQLException {
        Jdbc.transaccion(fuente, conexion -> {
            bloquearPuesto(conexion, puesto);
            ContribuyenteJdbcDao.exigirVigente(conexion, persona);
            Jdbc.actualizar(conexion, """
                    INSERT INTO asignaciones_puestos (puesto_id, comerciante_id, giro_comercial, asignado_por)
                    VALUES (?, ?, ?, ?)
                    """, puesto, persona, giro, usuario);
            return null;
        });
    }

    public void liberar(long puesto, long asignacion, long usuario) throws SQLException {
        Jdbc.transaccion(fuente, conexion -> {
            bloquearPuesto(conexion, puesto);
            int cambio = Jdbc.actualizar(conexion, """
                    UPDATE asignaciones_puestos
                    SET liberado_por = ?, liberado_en = GREATEST(CURRENT_TIMESTAMP(6), TIMESTAMPADD(MICROSECOND, 1, asignado_en))
                    WHERE puesto_id = ? AND id = ? AND liberado_en IS NULL
                    """, usuario, puesto, asignacion);
            if (cambio != 1) throw new IllegalArgumentException("La asignación ya no está vigente.");
            return null;
        });
    }

    private static void bloquearPuesto(Connection conexion, long puesto) throws SQLException {
        if (Jdbc.listar(conexion, "SELECT id FROM puestos WHERE id = ? AND baja_en IS NULL FOR UPDATE",
                f -> f.getLong(1), puesto).isEmpty()) throw new IllegalArgumentException("El puesto no está disponible.");
    }

    private static Puesto fila(ResultSet f) throws SQLException {
        return new Puesto(f.getLong("id"), f.getString("numero"), f.getString("mercado"), f.getString("sector"),
                f.getObject("asignacion", Long.class), f.getString("comerciante"));
    }
}
