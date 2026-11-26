package pe.gob.sigmud.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.ModuloMunicipal;
import pe.gob.sigmud.model.PanelMunicipal;
import pe.gob.sigmud.model.PanelMunicipal.Actividad;
import pe.gob.sigmud.model.PanelMunicipal.Cita;
import pe.gob.sigmud.model.PanelMunicipal.Indicador;
import pe.gob.sigmud.model.PanelMunicipal.Medicion;
import pe.gob.sigmud.util.ConexionBD;

public final class PanelJdbcDao {

    private static final String ULTIMO_ESTADO = """
            FROM tramites t JOIN historial_tramites h ON h.id = (
                SELECT MAX(h2.id) FROM historial_tramites h2 WHERE h2.tramite_id = t.id
            )
            """;
    private static final String ALCANCE_AREA = """
             AND (? OR %1$s IN (
                SELECT a.id FROM areas a WHERE a.baja_en IS NULL
                  AND CASE WHEN ? THEN a.codigo IN ('FISCALIZACION', 'DEFENSA_CIVIL')
                           ELSE a.id = (SELECT u.area_id FROM usuarios u WHERE u.id = ?)
                      END
            ))
            """;
    private static final String AGENDA_PROGRAMADA = """
            FROM citas c JOIN ventanillas v ON v.id = c.ventanilla_id
            WHERE c.estado = 'PROGRAMADA' AND c.inicio >= CURRENT_TIMESTAMP(6)
            """ + ALCANCE_AREA.formatted("v.area_id");
    private final ConexionPanel conexiones;

    public PanelJdbcDao() {
        conexiones = ConexionBD::abrir;
    }

    public PanelJdbcDao(DataSource fuente) {
        conexiones = fuente::getConnection;
    }

    public PanelMunicipal consultar(Identidad identidad, List<Indicador> indicadores) throws SQLException {
        if (indicadores.isEmpty()) {
            return new PanelMunicipal(List.of(), List.of(), List.of());
        }
        try (Connection conexion = conexiones.abrir()) {
            conexion.setReadOnly(true);
            List<Medicion> mediciones = new ArrayList<>();
            List<ConsultaActividad> fuentes = new ArrayList<>();
            for (Indicador indicador : indicadores) {
                mediciones.add(new Medicion(indicador, contar(conexion, identidad, indicador)));
                agregarActividad(fuentes, indicador);
            }
            List<Cita> proximas = indicadores.contains(Indicador.CITAS) ? proximasCitas(conexion, identidad) : List.of();
            return new PanelMunicipal(mediciones, actividad(conexion, identidad, fuentes), proximas);
        }
    }

    private static long contar(Connection conexion, Identidad identidad, Indicador indicador) throws SQLException {
        String consulta = switch (indicador) {
            case CONTRIBUYENTES -> "SELECT COUNT(*) FROM contribuyentes WHERE baja_en IS NULL";
            case TRAMITES -> "SELECT COUNT(*) " + ULTIMO_ESTADO
                    + "WHERE h.estado IN ('RECIBIDO', 'EN_TRAMITE')" + ALCANCE_AREA.formatted("h.area_id");
            case RECLAMOS -> "SELECT COUNT(*) FROM reclamos_emergencias r WHERE r.estado IN ('PENDIENTE', 'EN_ATENCION')"
                    + ALCANCE_AREA.formatted("r.area_id");
            case CITAS -> "SELECT COUNT(*) " + AGENDA_PROGRAMADA;
            case MERCADOS -> """
                    SELECT COUNT(*) FROM asignaciones_puestos ap
                    JOIN puestos p ON p.id = ap.puesto_id JOIN mercados m ON m.id = p.mercado_id
                    WHERE ap.liberado_en IS NULL AND p.baja_en IS NULL AND m.baja_en IS NULL
                    """;
            case ALMACEN -> """
                    SELECT COUNT(*) FROM (
                        SELECT m.id FROM materiales m LEFT JOIN lotes_material l ON l.material_id = m.id
                        WHERE m.baja_en IS NULL GROUP BY m.id, m.stock_minimo
                        HAVING COALESCE(SUM(l.stock), 0) <= m.stock_minimo
                    ) AS materiales_bajo_minimo
                    """;
        };
        try (PreparedStatement sql = conexion.prepareStatement(consulta)) {
            if (porArea(indicador)) {
                aplicarAlcance(sql, 1, identidad);
            }
            try (ResultSet resultado = sql.executeQuery()) {
                resultado.next();
                return resultado.getLong(1);
            }
        }
    }

    private static void agregarActividad(List<ConsultaActividad> fuentes, Indicador indicador) {
        String origen = switch (indicador) {
            case CONTRIBUYENTES -> null;
            case TRAMITES -> "SELECT 'TRAMITES' AS modulo, 'Actualización de trámite' AS evento, "
                    + "t.numero_expediente AS referencia, h.registrado_en AS fecha " + ULTIMO_ESTADO
                    + "WHERE h.registrado_en <= CURRENT_TIMESTAMP(6)" + ALCANCE_AREA.formatted("h.area_id");
            case RECLAMOS -> """
                    SELECT 'RECLAMOS' AS modulo, 'Registro de caso' AS evento, r.codigo_caso AS referencia,
                           r.registrado_en AS fecha FROM reclamos_emergencias r
                    WHERE r.registrado_en <= CURRENT_TIMESTAMP(6)
                    """ + ALCANCE_AREA.formatted("r.area_id");
            case CITAS -> """
                    SELECT 'CITAS' AS modulo, 'Registro de cita' AS evento, c.codigo_cita AS referencia,
                           c.registrado_en AS fecha FROM citas c JOIN ventanillas v ON v.id = c.ventanilla_id
                    WHERE c.registrado_en <= CURRENT_TIMESTAMP(6)
                    """ + ALCANCE_AREA.formatted("v.area_id");
            case MERCADOS -> """
                    SELECT 'MERCADOS' AS modulo, 'Asignación de puesto' AS evento,
                           CONCAT(m.codigo, ' · ', p.numero) AS referencia, ap.asignado_en AS fecha
                    FROM asignaciones_puestos ap JOIN puestos p ON p.id = ap.puesto_id
                    JOIN mercados m ON m.id = p.mercado_id
                    WHERE ap.asignado_en <= CURRENT_TIMESTAMP(6)
                    """;
            case ALMACEN -> """
                    SELECT 'ALMACEN' AS modulo, 'Movimiento de almacén' AS evento,
                           codigo_movimiento AS referencia, registrado_en AS fecha FROM movimientos_stock
                    WHERE registrado_en <= CURRENT_TIMESTAMP(6)
                    """;
        };
        if (origen != null) {
            fuentes.add(new ConsultaActividad(origen + " ORDER BY fecha DESC, referencia DESC LIMIT 5",
                    porArea(indicador)));
        }
    }

    private static List<Cita> proximasCitas(Connection conexion, Identidad identidad) throws SQLException {
        String consulta = "SELECT c.codigo_cita, v.nombre, c.inicio " + AGENDA_PROGRAMADA
                + " ORDER BY c.inicio, c.codigo_cita LIMIT 3";
        try (PreparedStatement sql = conexion.prepareStatement(consulta)) {
            aplicarAlcance(sql, 1, identidad);
            try (ResultSet filas = sql.executeQuery()) {
                List<Cita> citas = new ArrayList<>();
                while (filas.next()) {
                    citas.add(new Cita(filas.getString("codigo_cita"), filas.getString("nombre"),
                            filas.getObject("inicio", LocalDateTime.class)));
                }
                return citas;
            }
        }
    }

    private static List<Actividad> actividad(Connection conexion, Identidad identidad, List<ConsultaActividad> fuentes)
            throws SQLException {
        if (fuentes.isEmpty()) {
            return List.of();
        }
        String consulta = fuentes.size() == 1 ? fuentes.get(0).sql()
                : String.join(" UNION ALL ", fuentes.stream().map(fuente -> "(" + fuente.sql() + ")").toList())
                  + " ORDER BY fecha DESC, modulo, referencia DESC LIMIT 5";
        try (PreparedStatement sql = conexion.prepareStatement(consulta)) {
            int indice = 1;
            for (ConsultaActividad fuente : fuentes) {
                if (fuente.porArea()) {
                    aplicarAlcance(sql, indice, identidad);
                    indice += 3;
                }
            }
            try (ResultSet filas = sql.executeQuery()) {
                List<Actividad> actividad = new ArrayList<>();
                while (filas.next()) {
                    actividad.add(new Actividad(ModuloMunicipal.valueOf(filas.getString("modulo")),
                            filas.getString("evento"), filas.getString("referencia"),
                            filas.getObject("fecha", LocalDateTime.class)));
                }
                return actividad;
            }
        }
    }

    private static boolean porArea(Indicador indicador) {
        return indicador == Indicador.TRAMITES || indicador == Indicador.RECLAMOS || indicador == Indicador.CITAS;
    }

    private static void aplicarAlcance(PreparedStatement sql, int indice, Identidad identidad) throws SQLException {
        sql.setBoolean(indice, "ADMINISTRADOR".equals(identidad.codigoRol()));
        sql.setBoolean(indice + 1, "FISCALIZACION_DEFENSA_CIVIL".equals(identidad.codigoRol()));
        sql.setLong(indice + 2, identidad.id());
    }

    private record ConsultaActividad(String sql, boolean porArea) {}

    @FunctionalInterface
    private interface ConexionPanel {
        Connection abrir() throws SQLException;
    }
}
