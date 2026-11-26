package pe.gob.sigmud.service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import pe.gob.sigmud.dao.impl.AlmacenJdbcDao;
import pe.gob.sigmud.dao.impl.AlmacenJdbcDao.*;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla.Opcion;
import pe.gob.sigmud.util.Validacion;

public final class AlmacenService {
    public static final List<String> TIPOS = List.of("INGRESO", "SALIDA");
    private static final BigDecimal CANTIDAD_MAXIMA = new BigDecimal("99999999999.999");
    private final AlmacenJdbcDao dao;
    public AlmacenService() { this(new AlmacenJdbcDao()); }
    public AlmacenService(AlmacenJdbcDao dao) { this.dao = dao; }

    public Pagina<Material> listar(Identidad u, String q, String alerta, int pagina) throws SQLException {
        consultar(u);
        Validacion.texto(q, "búsqueda", 140, false);
        return dao.materiales(q, alerta, pagina);
    }
    public Material detalle(Identidad u, long id) throws SQLException {
        consultar(u);
        return dao.buscar(id).orElseThrow(() -> new IllegalArgumentException("El material no está disponible."));
    }
    public Resumen resumen(Identidad u) throws SQLException { consultar(u); return dao.resumen(); }
    public List<Lote> lotes(Identidad u, long id) throws SQLException { consultar(u); return dao.lotes(id); }
    public List<Opcion> areas(Identidad u) throws SQLException { gestionar(u); return dao.areas(u); }
    public Pagina<Movimiento> movimientos(Identidad u, long id, String tipo, String desde, String hasta, int pagina)
            throws SQLException {
        consultar(u);
        var intervalo = Validacion.intervalo(desde, hasta);
        return dao.movimientos(id, new FiltroMovimiento(Validacion.catalogo(TIPOS, tipo, "tipo", false),
                intervalo.desde(), intervalo.hastaExclusiva()), pagina);
    }
    public long guardarMaterial(Identidad u, Long id, String codigo, String nombre, String unidad, String minimo) throws SQLException {
        gestionar(u);
        try {
            return dao.guardarMaterial(id, Validacion.texto(codigo, "código", 40, id == null),
                    Validacion.texto(nombre, "nombre", 140, true), Validacion.texto(unidad, "unidad", 35, id == null),
                    cantidad(minimo, false));
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) throw new IllegalArgumentException("El código del material ya existe.");
            throw e;
        }
    }
    public void guardarLote(Identidad u, long material, Long lote, String codigo, String vence) throws SQLException {
        gestionar(u);
        try {
            dao.guardarLote(material, lote, Validacion.texto(codigo, "código de lote", 40, lote == null),
                    Validacion.fecha(vence, "vencimiento"));
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) throw new IllegalArgumentException("Este código de lote ya existe para el material.");
            throw e;
        }
    }
    public void movimiento(Identidad u, long material, long lote, String tipo, String unidades, Long area, String motivo)
            throws SQLException {
        gestionar(u);
        String clase = Validacion.catalogo(TIPOS, tipo, "movimiento", true);
        if ("SALIDA".equals(clase) && area == null) throw new IllegalArgumentException("Selecciona el área solicitante.");
        try {
            dao.movimiento(material, lote, Validacion.codigo("MOV"), clase, cantidad(unidades, true), area, u.id(),
                    Validacion.texto(motivo, "motivo", 250, true));
        } catch (SQLException e) {
            if ("45000".equals(e.getSQLState())) {
                throw new IllegalArgumentException("El saldo del lote no admite ese movimiento. Revisa la existencia disponible.");
            }
            if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) {
                throw new IllegalArgumentException("El lote está ocupado por otro movimiento. Revisa el saldo y vuelve a intentarlo.");
            }
            throw e;
        }
    }

    private static BigDecimal cantidad(String texto, boolean positiva) {
        if (texto != null && texto.matches("[0-9]{1,11}(\\.[0-9]{1,3})?")) {
            BigDecimal numero = new BigDecimal(texto);
            if ((!positiva || numero.signum() > 0) && numero.compareTo(CANTIDAD_MAXIMA) <= 0) return numero;
        }
        throw new IllegalArgumentException(positiva ? "Ingresa una cantidad mayor a cero, con hasta tres decimales."
                : "Ingresa un mínimo no negativo de hasta tres decimales.");
    }

    private static void consultar(Identidad u) { Validacion.permiso(u, "ALMACEN_CONSULTAR"); }
    private static void gestionar(Identidad u) { Validacion.permiso(u, "ALMACEN_GESTIONAR"); }
}
