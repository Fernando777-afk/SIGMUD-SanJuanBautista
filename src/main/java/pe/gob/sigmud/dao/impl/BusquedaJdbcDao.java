package pe.gob.sigmud.dao.impl;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import pe.gob.sigmud.dao.AlcanceAreas;
import pe.gob.sigmud.dao.Jdbc;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.ModuloMunicipal;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.util.ConexionBD;

public final class BusquedaJdbcDao {
    public enum Tipo {
        CONTRIBUYENTES("Contribuyentes", ModuloMunicipal.CONTRIBUYENTES, false),
        DOCUMENTOS("DNI y RUC", ModuloMunicipal.CONTRIBUYENTES, false),
        PREDIOS("Predios", ModuloMunicipal.CONTRIBUYENTES, false),
        EXPEDIENTES("Expedientes", ModuloMunicipal.TRAMITES, true),
        RECLAMOS("Reclamos y emergencias", ModuloMunicipal.RECLAMOS, true),
        CITAS("Citas", ModuloMunicipal.CITAS, true),
        PUESTOS("Puestos", ModuloMunicipal.MERCADOS, false),
        MATERIALES("Materiales", ModuloMunicipal.ALMACEN, false);
        private final String etiqueta;
        private final ModuloMunicipal modulo;
        private final boolean porArea;
        Tipo(String etiqueta, ModuloMunicipal modulo, boolean porArea) {
            this.etiqueta = etiqueta;
            this.modulo = modulo;
            this.porArea = porArea;
        }
        public String etiqueta() { return etiqueta; }
        public ModuloMunicipal modulo() { return modulo; }
        public boolean porArea() { return porArea; }
    }

    public enum Orden { TIPO, TITULO }
    public record Resultado(Tipo tipo, long id, String codigo, String titulo, String referencia) {}
    private record Fuente(String sql, int textos, String area) {}

    private final Jdbc.Conexion conexiones;
    public BusquedaJdbcDao() { this(ConexionBD::abrir); }
    public BusquedaJdbcDao(Jdbc.Conexion conexiones) { this.conexiones = conexiones; }

    public Pagina<Resultado> buscar(Identidad usuario, List<Tipo> tipos, String texto, Long area, Orden orden, int pagina)
            throws SQLException {
        if (tipos.isEmpty()) return new Pagina<>(List.of(), pagina, false);
        String patron = Jdbc.contiene(texto);
        List<Object> parametros = new ArrayList<>();
        List<String> consultas = new ArrayList<>();
        for (Tipo tipo : tipos) {
            if (area != null && !tipo.porArea) continue;
            Fuente fuente = fuente(tipo);
            String sql = "SELECT '" + tipo.name() + "' AS clase, " + tipo.ordinal() + " AS orden_tipo, " + fuente.sql();
            for (int i = 0; i < fuente.textos(); i++) parametros.add(patron);
            if (tipo.porArea) {
                sql += " AND " + AlcanceAreas.condicion(fuente.area());
                parametros.addAll(List.of(AlcanceAreas.parametros(usuario)));
                if (area != null) {
                    sql += " AND " + fuente.area() + " = ?";
                    parametros.add(area);
                }
            }
            consultas.add(sql);
        }
        if (consultas.isEmpty()) return new Pagina<>(List.of(), pagina, false);
        String consulta = String.join(" UNION ALL ", consultas) + (orden == Orden.TITULO
                ? " ORDER BY titulo, orden_tipo, id, codigo" : " ORDER BY orden_tipo, titulo, id, codigo");
        try (Connection conexion = conexiones.abrir()) {
            return Jdbc.pagina(conexion, consulta, f -> new Resultado(Tipo.valueOf(f.getString("clase")),
                    f.getLong("id"), f.getString("codigo"), f.getString("titulo"), f.getString("referencia")),
                    pagina, parametros.toArray());
        }
    }

    public List<pe.gob.sigmud.model.Pantalla.Opcion> areas(Identidad usuario) throws SQLException {
        try (Connection conexion = conexiones.abrir()) { return AlcanceAreas.areas(conexion, usuario, true); }
    }

    private static Fuente fuente(Tipo t) {
        return switch (t) {
            case CONTRIBUYENTES -> new Fuente("""
                    c.id AS id, COALESCE(c.dni, c.ruc) AS codigo, c.nombre_razon_social AS titulo,
                    c.tipo_persona AS referencia FROM contribuyentes c
                    WHERE c.nombre_razon_social LIKE ? ESCAPE '!'
                    """, 1, null);
            case DOCUMENTOS -> new Fuente("""
                    c.id AS id, CONCAT_WS(' / ', c.dni, c.ruc) AS codigo, c.nombre_razon_social AS titulo,
                    'Documento de contribuyente' AS referencia FROM contribuyentes c
                    WHERE c.dni LIKE ? ESCAPE '!' OR c.ruc LIKE ? ESCAPE '!'
                    """, 2, null);
            case PREDIOS -> new Fuente("""
                    c.id AS id, p.codigo_predio AS codigo, c.nombre_razon_social AS titulo,
                    'Predio asociado' AS referencia FROM contribuyentes_predios p
                    JOIN contribuyentes c ON c.id = p.contribuyente_id WHERE p.codigo_predio LIKE ? ESCAPE '!'
                    """, 1, null);
            case EXPEDIENTES -> new Fuente("""
                    t.id AS id, t.numero_expediente AS codigo, t.tipo_tramite AS titulo, a.nombre AS referencia
                    FROM tramites t JOIN historial_tramites h ON h.id = (SELECT MAX(h2.id) FROM historial_tramites h2 WHERE h2.tramite_id = t.id)
                    JOIN areas a ON a.id = h.area_id
                    WHERE (t.numero_expediente LIKE ? ESCAPE '!' OR t.tipo_tramite LIKE ? ESCAPE '!')
                    """, 2, "h.area_id");
            case RECLAMOS -> new Fuente("""
                    r.id AS id, r.codigo_caso AS codigo, r.tipo AS titulo, a.nombre AS referencia
                    FROM reclamos_emergencias r JOIN areas a ON a.id = r.area_id
                    WHERE r.codigo_caso LIKE ? ESCAPE '!'
                    """, 1, "r.area_id");
            case CITAS -> new Fuente("""
                    c.id AS id, c.codigo_cita AS codigo, v.nombre AS titulo, a.nombre AS referencia FROM citas c
                    JOIN ventanillas v ON v.id = c.ventanilla_id JOIN areas a ON a.id = v.area_id
                    WHERE (c.codigo_cita LIKE ? ESCAPE '!' OR v.nombre LIKE ? ESCAPE '!')
                    """, 2, "v.area_id");
            case PUESTOS -> new Fuente("""
                    p.id AS id, p.numero AS codigo, m.nombre AS titulo, s.nombre AS referencia
                    FROM puestos p JOIN mercados m ON m.id = p.mercado_id JOIN sectores_mercado s ON s.id = p.sector_id
                    WHERE p.baja_en IS NULL AND m.baja_en IS NULL
                      AND (p.numero LIKE ? ESCAPE '!' OR m.nombre LIKE ? ESCAPE '!' OR s.nombre LIKE ? ESCAPE '!')
                    """, 3, null);
            case MATERIALES -> new Fuente("""
                    m.id AS id, m.codigo AS codigo, m.nombre AS titulo, m.unidad_medida AS referencia
                    FROM materiales m WHERE m.baja_en IS NULL AND (m.codigo LIKE ? ESCAPE '!' OR m.nombre LIKE ? ESCAPE '!')
                    """, 2, null);
        };
    }
}
