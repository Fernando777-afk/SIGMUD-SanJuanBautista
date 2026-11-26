package pe.gob.sigmud.dao.impl;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import pe.gob.sigmud.dao.Jdbc;
import pe.gob.sigmud.model.Contribuyente;
import pe.gob.sigmud.model.Contribuyente.Tipo;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla.Opcion;
import pe.gob.sigmud.util.ConexionBD;

public final class ContribuyenteJdbcDao {
    private static final String COLUMNAS = """
            SELECT c.id, c.tipo_persona, c.dni, c.ruc, c.nombre_razon_social, c.telefono,
                   c.direccion, c.fecha_nacimiento, c.baja_en IS NULL AS activo FROM contribuyentes c
            """;
    private static final String PERSONA = """
            c.nombre_razon_social LIKE ? ESCAPE '!' OR c.dni LIKE ? ESCAPE '!' OR c.ruc LIKE ? ESCAPE '!'
            """;
    private static final String PREDIO = """
            OR EXISTS (SELECT 1 FROM contribuyentes_predios p WHERE p.contribuyente_id = c.id
                       AND p.codigo_predio LIKE ? ESCAPE '!')
            """;
    private static final String BUSQUEDA = "(" + PERSONA + PREDIO + ")";
    private final Jdbc.Conexion fuente;

    public ContribuyenteJdbcDao() {
        this(ConexionBD::abrir);
    }

    public ContribuyenteJdbcDao(Jdbc.Conexion fuente) {
        this.fuente = fuente;
    }

    public Pagina<Contribuyente> listar(String texto, Tipo tipo, String estado, int pagina, boolean incluirPredios)
            throws SQLException {
        String criterio = incluirPredios ? BUSQUEDA : "(" + PERSONA + ")";
        String sql = COLUMNAS + " WHERE " + criterio + " AND (? IS NULL OR c.tipo_persona = ?)"
                + switch (estado) {
                    case "activos" -> " AND c.baja_en IS NULL";
                    case "bajas" -> " AND c.baja_en IS NOT NULL";
                    default -> "";
                } + " ORDER BY c.nombre_razon_social, c.id";
        String patron = Jdbc.contiene(texto);
        String categoria = tipo == null ? null : tipo.name();
        try (Connection conexion = fuente.abrir()) {
            Object[] parametros = incluirPredios ? new Object[] {patron, patron, patron, patron, categoria, categoria}
                    : new Object[] {patron, patron, patron, categoria, categoria};
            return Jdbc.pagina(conexion, sql, ContribuyenteJdbcDao::fila, pagina, parametros);
        }
    }

    public Optional<Contribuyente> buscar(long id) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.listar(conexion, COLUMNAS + " WHERE c.id = ?", ContribuyenteJdbcDao::fila, id)
                    .stream().findFirst();
        }
    }

    public List<String> predios(long id) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.listar(conexion, "SELECT codigo_predio FROM contribuyentes_predios WHERE contribuyente_id = ? ORDER BY codigo_predio",
                    fila -> fila.getString(1), id);
        }
    }

    public static List<Opcion> opciones(Connection conexion, String texto, Long seleccionado) throws SQLException {
        String patron = Jdbc.contiene(texto);
        return Jdbc.listar(conexion, """
                SELECT c.id, c.nombre_razon_social, COALESCE(c.dni, c.ruc) AS documento
                FROM contribuyentes c WHERE c.baja_en IS NULL AND (c.id = ? OR
                """ + BUSQUEDA + ") ORDER BY CASE WHEN c.id = ? THEN 0 ELSE 1 END, c.nombre_razon_social, c.id LIMIT 50",
                fila -> new Opcion(fila.getLong("id"), fila.getString("nombre_razon_social") + " · " + fila.getString("documento")),
                seleccionado, patron, patron, patron, patron, seleccionado);
    }

    public long guardar(Contribuyente datos) throws SQLException {
        return Jdbc.transaccion(fuente, conexion -> {
            Object[] campos = {datos.tipo().name(), datos.dni(), datos.ruc(), datos.nombre(), datos.telefono(),
                datos.direccion(), datos.nacimiento()};
            if (datos.id() == 0) {
                return Jdbc.insertar(conexion, """
                        INSERT INTO contribuyentes (tipo_persona, dni, ruc, nombre_razon_social, telefono, direccion, fecha_nacimiento)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, campos);
            }
            exigirVigente(conexion, datos.id());
            Jdbc.actualizar(conexion, """
                    UPDATE contribuyentes SET tipo_persona = ?, dni = ?, ruc = ?, nombre_razon_social = ?,
                        telefono = ?, direccion = ?, fecha_nacimiento = ? WHERE id = ?
                    """, Jdbc.unir(campos, datos.id()));
            return datos.id();
        });
    }

    public void vincular(long id, String predio) throws SQLException {
        Jdbc.transaccion(fuente, conexion -> {
            exigirVigente(conexion, id);
            Jdbc.actualizar(conexion, "INSERT INTO contribuyentes_predios (contribuyente_id, codigo_predio) VALUES (?, ?)",
                    id, predio);
            return null;
        });
    }

    public void baja(long id) throws SQLException {
        Jdbc.transaccion(fuente, conexion -> {
            exigirVigente(conexion, id);
            Jdbc.actualizar(conexion, """
                    UPDATE contribuyentes SET baja_en = GREATEST(CURRENT_TIMESTAMP(6), registrado_en) WHERE id = ?
                    """, id);
            return null;
        });
    }

    public static void exigirVigente(Connection conexion, long id) throws SQLException {
        // La fila permanece estable hasta que se complete la operación que la referencia.
        boolean vigente = Jdbc.listar(conexion,
                "SELECT baja_en IS NULL FROM contribuyentes WHERE id = ? FOR UPDATE",
                fila -> fila.getBoolean(1), id).stream().findFirst().orElse(false);
        if (!vigente) throw new IllegalArgumentException("Selecciona un contribuyente vigente.");
    }

    private static Contribuyente fila(ResultSet f) throws SQLException {
        return new Contribuyente(f.getLong("id"), Tipo.valueOf(f.getString("tipo_persona")), f.getString("dni"),
                f.getString("ruc"), f.getString("nombre_razon_social"), f.getString("telefono"), f.getString("direccion"),
                f.getObject("fecha_nacimiento", LocalDate.class), f.getBoolean("activo"));
    }
}
