package pe.gob.sigmud.dao.impl;

import java.math.BigDecimal;
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
import pe.gob.sigmud.util.ConexionBD;

public final class AlmacenJdbcDao {
    private static final String SELECT = """
            SELECT m.id, m.codigo, m.nombre, m.unidad_medida, m.stock_minimo,
                   COALESCE(SUM(l.stock), 0) AS stock
            FROM materiales m LEFT JOIN lotes_material l ON l.material_id = m.id
            WHERE m.baja_en IS NULL
            """;
    private static final String GRUPO = " GROUP BY m.id, m.codigo, m.nombre, m.unidad_medida, m.stock_minimo";
    private final Jdbc.Conexion fuente;

    public record Material(long id, String codigo, String nombre, String unidad, BigDecimal minimo, BigDecimal stock) {
        public boolean alerta() { return stock.compareTo(minimo) <= 0; }
    }
    public record Lote(long id, String codigo, LocalDate vencimiento, BigDecimal stock) {}
    public record Movimiento(String codigo, String lote, String tipo, BigDecimal cantidad, String area,
            String motivo, LocalDateTime fecha) {}
    public record Resumen(long materiales, long stockBajo, long lotesPorVencer) {}
    public record FiltroMovimiento(String tipo, LocalDate desde, LocalDate hasta) {}

    public AlmacenJdbcDao() { this(ConexionBD::abrir); }
    public AlmacenJdbcDao(Jdbc.Conexion fuente) { this.fuente = fuente; }

    public Pagina<Material> materiales(String q, String alerta, int pagina) throws SQLException {
        String filtro = switch (alerta) {
            case "VENCIMIENTOS" -> """
                    AND EXISTS (SELECT 1 FROM lotes_material lv WHERE lv.material_id = m.id AND lv.stock > 0
                                AND lv.vence_en <= CURRENT_DATE + INTERVAL 30 DAY)
                    """;
            default -> "";
        };
        String sql = SELECT + " AND (m.codigo LIKE ? ESCAPE '!' OR m.nombre LIKE ? ESCAPE '!') "
                + filtro + GRUPO + ("BAJO".equals(alerta) ? " HAVING COALESCE(SUM(l.stock), 0) <= m.stock_minimo" : "")
                + " ORDER BY m.nombre, m.id";
        String patron = Jdbc.contiene(q);
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.pagina(conexion, sql, AlmacenJdbcDao::material, pagina, patron, patron);
        }
    }

    public Optional<Material> buscar(long id) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.listar(conexion, SELECT + " AND m.id = ?" + GRUPO, AlmacenJdbcDao::material, id).stream().findFirst();
        }
    }

    public List<Lote> lotes(long id) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.listar(conexion, "SELECT id, codigo_lote, vence_en, stock FROM lotes_material WHERE material_id = ? ORDER BY vence_en IS NULL, vence_en, id",
                    f -> new Lote(f.getLong(1), f.getString(2), f.getObject(3, LocalDate.class), f.getBigDecimal(4)), id);
        }
    }

    public Pagina<Movimiento> movimientos(long id, FiltroMovimiento filtro, int pagina) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.pagina(conexion, """
                    SELECT ms.codigo_movimiento, l.codigo_lote, ms.tipo, ms.cantidad, a.nombre, ms.motivo,
                           ms.registrado_en
                    FROM movimientos_stock ms JOIN lotes_material l ON l.id = ms.lote_id
                    LEFT JOIN areas a ON a.id = ms.area_solicitante_id
                    WHERE l.material_id = ? AND (? IS NULL OR ms.tipo = ?)
                      AND (? IS NULL OR ms.registrado_en >= ?) AND (? IS NULL OR ms.registrado_en < ?)
                    ORDER BY ms.registrado_en DESC, ms.id DESC
                    """, f -> new Movimiento(f.getString(1), f.getString(2), f.getString(3), f.getBigDecimal(4), f.getString(5),
                        f.getString(6), f.getObject(7, LocalDateTime.class)), pagina, id, filtro.tipo(), filtro.tipo(),
                    filtro.desde(), filtro.desde(), filtro.hasta(), filtro.hasta());
        }
    }

    public Resumen resumen() throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            return Jdbc.listar(conexion, """
                    SELECT COUNT(*) AS materiales, COALESCE(SUM(stock <= stock_minimo), 0) AS bajos,
                        (SELECT COUNT(*) FROM lotes_material l JOIN materiales m ON m.id = l.material_id
                           WHERE m.baja_en IS NULL AND l.stock > 0 AND l.vence_en <= CURRENT_DATE + INTERVAL 30 DAY) AS lotes
                    FROM (
                    """ + SELECT + GRUPO + ") AS inventario",
                    f -> new Resumen(f.getLong(1), f.getLong(2), f.getLong(3))).get(0);
        }
    }

    public List<Opcion> areas(Identidad u) throws SQLException {
        try (Connection conexion = fuente.abrir()) { return AlcanceAreas.areas(conexion, u, false); }
    }

    public long guardarMaterial(Long id, String codigo, String nombre, String unidad, BigDecimal minimo) throws SQLException {
        return Jdbc.transaccion(fuente, conexion -> {
            if (id == null) return Jdbc.insertar(conexion, """
                    INSERT INTO materiales (codigo, nombre, unidad_medida, stock_minimo) VALUES (?, ?, ?, ?)
                    """, codigo, nombre, unidad, minimo);
            verificarMaterial(conexion, id);
            Jdbc.actualizar(conexion, "UPDATE materiales SET nombre = ?, stock_minimo = ? WHERE id = ?", nombre, minimo, id);
            return id;
        });
    }

    public void guardarLote(long material, Long lote, String codigo, LocalDate vence) throws SQLException {
        Jdbc.transaccion(fuente, conexion -> {
            verificarMaterial(conexion, material);
            if (lote == null) {
                Jdbc.actualizar(conexion, "INSERT INTO lotes_material (material_id, codigo_lote, vence_en) VALUES (?, ?, ?)",
                        material, codigo, vence);
            } else if (Jdbc.actualizar(conexion, "UPDATE lotes_material SET vence_en = ? WHERE id = ? AND material_id = ?",
                    vence, lote, material) == 0) {
                throw new IllegalArgumentException("El lote no está disponible para este material.");
            }
            return null;
        });
    }

    public void movimiento(long material, long lote, String codigo, String tipo, BigDecimal cantidad,
            Long area, long usuario, String motivo) throws SQLException {
        Jdbc.transaccion(fuente, conexion -> {
            // El trigger de MySQL modifica el saldo sobre la fila bloqueada. Es la única escritura de stock.
            verificarMaterial(conexion, material);
            if (Jdbc.listar(conexion, "SELECT id FROM lotes_material WHERE id = ? AND material_id = ?",
                    f -> f.getLong(1), lote, material).isEmpty()) {
                throw new IllegalArgumentException("Selecciona un lote del material.");
            }
            if (area != null) TramiteJdbcDao.exigirAreaVigente(conexion, area);
            Jdbc.actualizar(conexion, """
                    INSERT INTO movimientos_stock (codigo_movimiento, lote_id, tipo, cantidad,
                        area_solicitante_id, registrado_por, motivo) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, codigo, lote, tipo, cantidad, area, usuario, motivo);
            return null;
        });
    }

    private static void verificarMaterial(Connection conexion, long material) throws SQLException {
        if (Jdbc.listar(conexion, "SELECT id FROM materiales WHERE id = ? AND baja_en IS NULL FOR UPDATE",
                f -> f.getLong(1), material).isEmpty()) throw new IllegalArgumentException("El material no está disponible.");
    }

    private static Material material(ResultSet f) throws SQLException {
        return new Material(f.getLong("id"), f.getString("codigo"), f.getString("nombre"), f.getString("unidad_medida"),
                f.getBigDecimal("stock_minimo"), f.getBigDecimal("stock"));
    }
}
