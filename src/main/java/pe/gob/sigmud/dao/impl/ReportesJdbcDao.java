package pe.gob.sigmud.dao.impl;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import pe.gob.sigmud.dao.AlcanceAreas;
import pe.gob.sigmud.dao.Jdbc;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.ModuloMunicipal;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla.Opcion;
import pe.gob.sigmud.util.ConexionBD;

public final class ReportesJdbcDao {
    public enum Reporte {
        CONTRIBUYENTES("Contribuyentes registrados", ModuloMunicipal.CONTRIBUYENTES,
                "Personas, empresas y bajas según fecha de registro.", List.of("Tipo", "Vigentes", "Bajas", "Total"),
                "c.registrado_en", null, null, null, List.of()),
        TRAMITES_ESTADO("Trámites por estado", ModuloMunicipal.TRAMITES,
                "Agrupa según el estado actual y la fecha del último movimiento.", List.of("Estado", "Expedientes"),
                "h.registrado_en", "h.area_id", "h.estado", "h.area_id", List.of("RECIBIDO", "EN_TRAMITE", "ATENDIDO", "ARCHIVADO")),
        TRAMITES_AREA("Trámites atendidos por área", ModuloMunicipal.TRAMITES,
                "Cuenta expedientes distintos con atención en el período.", List.of("Área", "Expedientes atendidos"),
                "h.registrado_en", "h.area_id", null, "h.area_id", List.of()),
        RECLAMOS("Reclamos y emergencias", ModuloMunicipal.RECLAMOS,
                "Agrupa por prioridad. El período usa la fecha de atención o, si está abierto, la de registro.",
                List.of("Área", "Prioridad", "Estado", "Casos"), "COALESCE(r.atendido_en, r.registrado_en)", "r.area_id",
                "r.estado", "r.area_id", List.of("PENDIENTE", "EN_ATENCION", "ATENDIDO", "CANCELADO")),
        CITAS("Citas por estado y período", ModuloMunicipal.CITAS,
                "El período corresponde a la fecha de inicio de la cita.", List.of("Área", "Estado", "Citas"),
                "c.inicio", "v.area_id", "c.estado", "v.area_id", List.of("PROGRAMADA", "ATENDIDA", "CANCELADA", "NO_ASISTIO")),
        OCUPACION("Ocupación de puestos", ModuloMunicipal.MERCADOS,
                "Puestos vigentes y asignaciones activas por mercado.", List.of("Mercado", "Puestos", "Ocupados", "Libres", "Ocupación (%)"),
                null, null, null, null, List.of()),
        STOCK("Materiales con stock bajo", ModuloMunicipal.ALMACEN,
                "Existencias iguales o inferiores al mínimo de cada material.", List.of("Código", "Material", "Unidad", "Existencia", "Mínimo"),
                null, null, null, null, List.of()),
        MOVIMIENTOS("Movimientos de almacén", ModuloMunicipal.ALMACEN,
                "Historial de ingresos y salidas por fecha de registro.", List.of("Código", "Material", "Lote", "Tipo", "Cantidad", "Área", "Fecha"),
                "x.registrado_en", "x.area_solicitante_id", "x.tipo", null, List.of("INGRESO", "SALIDA"));

        private final String titulo;
        private final ModuloMunicipal modulo;
        private final String descripcion;
        private final List<String> columnas;
        private final String campoFecha;
        private final String campoArea;
        private final String campoEstado;
        private final String campoAlcance;
        private final List<String> estados;

        Reporte(String titulo, ModuloMunicipal modulo, String descripcion, List<String> columnas, String campoFecha,
                String campoArea, String campoEstado, String campoAlcance, List<String> estados) {
            this.titulo = titulo;
            this.modulo = modulo;
            this.descripcion = descripcion;
            this.columnas = columnas;
            this.campoFecha = campoFecha;
            this.campoArea = campoArea;
            this.campoEstado = campoEstado;
            this.campoAlcance = campoAlcance;
            this.estados = estados;
        }

        public String titulo() { return titulo; }
        public ModuloMunicipal modulo() { return modulo; }
        public String descripcion() { return descripcion; }
        public List<String> columnas() { return columnas; }
        public boolean fechas() { return campoFecha != null; }
        public boolean areas() { return campoArea != null; }
        public List<String> estados() { return estados; }
    }

    public record Filtros(LocalDate desde, LocalDate hasta, Long area, String estado) {}
    private record Consulta(String sql, Object[] parametros) {}

    private final Jdbc.Conexion fuente;
    public ReportesJdbcDao() { this(ConexionBD::abrir); }
    public ReportesJdbcDao(Jdbc.Conexion fuente) { this.fuente = fuente; }

    public Pagina<List<String>> listar(Reporte reporte, Identidad usuario, Filtros filtros, int pagina) throws SQLException {
        Consulta consulta = consulta(reporte, usuario, filtros);
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.pagina(conexion, consulta.sql(), f -> leer(f, reporte.columnas().size()), pagina, consulta.parametros());
        }
    }

    public List<List<String>> exportar(Reporte reporte, Identidad usuario, Filtros filtros) throws SQLException {
        Consulta consulta = consulta(reporte, usuario, filtros);
        try (Connection conexion = fuente.abrir()) {
            var filas = Jdbc.listar(conexion, consulta.sql() + " LIMIT ?", f -> leer(f, reporte.columnas().size()),
                    Jdbc.unir(consulta.parametros(), 10_001));
            if (filas.size() > 10_000) throw new IllegalArgumentException("Acota los filtros para exportar hasta 10 000 filas.");
            return filas;
        }
    }

    public List<Opcion> areas(Reporte reporte, Identidad usuario) throws SQLException {
        try (Connection conexion = fuente.abrir()) { return AlcanceAreas.areas(conexion, usuario, reporte.campoAlcance != null); }
    }

    private static Consulta consulta(Reporte r, Identidad usuario, Filtros f) {
        StringBuilder sql = new StringBuilder(origen(r));
        List<Object> parametros = new ArrayList<>();
        if (r.campoAlcance != null) {
            sql.append(" AND ").append(AlcanceAreas.condicion(r.campoAlcance));
            parametros.addAll(List.of(AlcanceAreas.parametros(usuario)));
        }
        condicion(sql, parametros, r.campoFecha, ">=", f.desde());
        condicion(sql, parametros, r.campoFecha, "<", f.hasta());
        condicion(sql, parametros, r.campoArea, "=", f.area());
        condicion(sql, parametros, r.campoEstado, "=", f.estado());
        return new Consulta(sql.append(agrupar(r)).toString(), parametros.toArray());
    }

    private static void condicion(StringBuilder sql, List<Object> parametros, String columna, String operador, Object valor) {
        if (columna != null && valor != null) {
            sql.append(" AND ").append(columna).append(' ').append(operador).append(" ?");
            parametros.add(valor);
        }
    }

    private static String origen(Reporte r) {
        return switch (r) {
            case CONTRIBUYENTES -> """
                    SELECT c.tipo_persona, SUM(c.baja_en IS NULL), SUM(c.baja_en IS NOT NULL), COUNT(*)
                    FROM contribuyentes c WHERE TRUE
                    """;
            case TRAMITES_ESTADO -> """
                    SELECT h.estado, COUNT(*) FROM tramites t JOIN historial_tramites h ON h.id =
                      (SELECT MAX(x.id) FROM historial_tramites x WHERE x.tramite_id = t.id) WHERE TRUE
                    """;
            case TRAMITES_AREA -> """
                    SELECT a.nombre, COUNT(DISTINCT h.tramite_id) FROM historial_tramites h
                      JOIN areas a ON a.id = h.area_id WHERE h.estado = 'ATENDIDO'
                    """;
            case RECLAMOS -> """
                    SELECT a.nombre, r.prioridad, r.estado, COUNT(*) FROM reclamos_emergencias r
                      JOIN areas a ON a.id = r.area_id WHERE TRUE
                    """;
            case CITAS -> """
                    SELECT a.nombre, c.estado, COUNT(*) FROM citas c JOIN ventanillas v ON v.id = c.ventanilla_id
                      JOIN areas a ON a.id = v.area_id WHERE TRUE
                    """;
            case OCUPACION -> """
                    SELECT m.nombre, COUNT(p.id), COUNT(ap.id), COUNT(p.id) - COUNT(ap.id),
                      ROUND(100.0 * COUNT(ap.id) / NULLIF(COUNT(p.id), 0), 2)
                    FROM mercados m LEFT JOIN puestos p ON p.mercado_id = m.id AND p.baja_en IS NULL
                    LEFT JOIN asignaciones_puestos ap ON ap.puesto_vigente_id = p.id WHERE m.baja_en IS NULL
                    """;
            case STOCK -> """
                    SELECT m.codigo, m.nombre, m.unidad_medida, COALESCE(SUM(l.stock), 0), m.stock_minimo
                    FROM materiales m LEFT JOIN lotes_material l ON l.material_id = m.id WHERE m.baja_en IS NULL
                    """;
            case MOVIMIENTOS -> """
                    SELECT x.codigo_movimiento, m.nombre, l.codigo_lote, x.tipo, x.cantidad, a.nombre, x.registrado_en
                    FROM movimientos_stock x JOIN lotes_material l ON l.id = x.lote_id
                      JOIN materiales m ON m.id = l.material_id LEFT JOIN areas a ON a.id = x.area_solicitante_id WHERE TRUE
                    """;
        };
    }

    private static String agrupar(Reporte r) {
        return switch (r) {
            case CONTRIBUYENTES -> " GROUP BY c.tipo_persona ORDER BY c.tipo_persona";
            case TRAMITES_ESTADO -> " GROUP BY h.estado ORDER BY h.estado";
            case TRAMITES_AREA -> " GROUP BY a.id, a.nombre ORDER BY a.nombre, a.id";
            case RECLAMOS -> " GROUP BY a.id, a.nombre, r.prioridad, r.estado ORDER BY a.nombre, a.id, r.prioridad DESC, r.estado";
            case CITAS -> " GROUP BY a.id, a.nombre, c.estado ORDER BY a.nombre, a.id, c.estado";
            case OCUPACION -> " GROUP BY m.id, m.nombre ORDER BY m.nombre, m.id";
            case STOCK -> " GROUP BY m.id, m.codigo, m.nombre, m.unidad_medida, m.stock_minimo"
                    + " HAVING COALESCE(SUM(l.stock), 0) <= m.stock_minimo ORDER BY m.nombre, m.id";
            case MOVIMIENTOS -> " ORDER BY x.registrado_en DESC, x.id DESC";
        };
    }

    private static List<String> leer(java.sql.ResultSet fila, int columnas) throws SQLException {
        List<String> resultado = new ArrayList<>(columnas);
        for (int i = 1; i <= columnas; i++) resultado.add(fila.getString(i));
        return resultado;
    }
}
