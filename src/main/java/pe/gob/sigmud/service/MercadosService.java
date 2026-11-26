package pe.gob.sigmud.service;

import java.sql.SQLException;
import java.util.List;
import pe.gob.sigmud.dao.impl.MercadoJdbcDao;
import pe.gob.sigmud.dao.impl.MercadoJdbcDao.*;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla.Opcion;
import pe.gob.sigmud.util.Validacion;

public final class MercadosService {
    private final MercadoJdbcDao dao;
    public MercadosService() { this(new MercadoJdbcDao()); }
    public MercadosService(MercadoJdbcDao dao) { this.dao = dao; }

    public Pagina<Puesto> puestos(Identidad u, String numero, Long mercado, Long sector, String estado, int pagina) throws SQLException {
        consultar(u);
        Validacion.texto(numero, "número", 30, false);
        return dao.puestos(numero, mercado, sector, estado, pagina);
    }
    public Puesto detalle(Identidad u, long id) throws SQLException {
        consultar(u);
        return dao.puesto(id).orElseThrow(() -> new IllegalArgumentException("El puesto no está disponible."));
    }
    public Pagina<Asignacion> historial(Identidad u, long id, int numero) throws SQLException {
        consultar(u);
        return dao.historial(id, numero);
    }
    public List<Ocupacion> ocupacion(Identidad u) throws SQLException { consultar(u); return dao.ocupacion(); }
    public List<Entrada> catalogo(Identidad u, Catalogo c) throws SQLException { consultar(u); return dao.catalogo(c); }
    public List<Opcion> contribuyentes(Identidad u, String q) throws SQLException {
        gestionar(u);
        Validacion.texto(q, "búsqueda", 180, false);
        return dao.contribuyentes(q);
    }
    public void guardarCatalogo(Identidad u, Catalogo c, Long id, String codigo, String nombre) throws SQLException {
        gestionar(u);
        try {
            dao.guardarCatalogo(c, id, Validacion.texto(codigo, "código", 35, id == null),
                    Validacion.texto(nombre, "nombre", c == Catalogo.MERCADOS ? 140 : 100, true));
        } catch (SQLException e) {
            if (c == Catalogo.SECTORES && (e.getErrorCode() == 1142 || e.getErrorCode() == 1143)) {
                throw new IllegalArgumentException("La instalación necesita actualizar los permisos del catálogo de sectores.");
            }
            if (e.getErrorCode() == 1062) throw new IllegalArgumentException("El código ya existe.");
            throw e;
        }
    }
    public long altaPuesto(Identidad u, long mercado, long sector, String numero) throws SQLException {
        gestionar(u);
        try {
            return dao.altaPuesto(mercado, sector, Validacion.texto(numero, "número del puesto", 30, true));
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) throw new IllegalArgumentException("Ese puesto ya existe en el mercado y sector.");
            throw e;
        }
    }
    public void asignar(Identidad u, long puesto, long persona, String giro) throws SQLException {
        gestionar(u);
        try {
            dao.asignar(puesto, persona, Validacion.texto(giro, "giro comercial", 140, true), u.id());
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) throw new IllegalArgumentException("El puesto ya cuenta con una asignación vigente.");
            throw e;
        }
    }
    public void liberar(Identidad u, long puesto, long asignacion) throws SQLException {
        gestionar(u);
        dao.liberar(puesto, asignacion, u.id());
    }
    private static void consultar(Identidad u) { Validacion.permiso(u, "MERCADOS_CONSULTAR"); }
    private static void gestionar(Identidad u) { Validacion.permiso(u, "MERCADOS_GESTIONAR"); }
}
