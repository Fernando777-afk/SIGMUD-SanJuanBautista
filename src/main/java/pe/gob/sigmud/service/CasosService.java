package pe.gob.sigmud.service;

import java.sql.SQLException;
import java.util.List;
import pe.gob.sigmud.dao.impl.CasoJdbcDao;
import pe.gob.sigmud.dao.impl.CasoJdbcDao.Alta;
import pe.gob.sigmud.dao.impl.CasoJdbcDao.Filtro;
import pe.gob.sigmud.model.CasoMunicipal;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla.Opcion;
import pe.gob.sigmud.util.Validacion;

public final class CasosService {
    public static final List<String> TIPOS = List.of("RECLAMO", "EMERGENCIA");
    public static final List<String> PRIORIDADES = List.of("BAJA", "MEDIA", "ALTA", "CRITICA");
    public static final String PRIORIDAD_URGENTE = "URGENTE";
    public static final List<String> ESTADOS = List.of("PENDIENTE", "EN_ATENCION", "ATENDIDO", "CANCELADO");
    private final CasoJdbcDao dao;

    public CasosService() { this(new CasoJdbcDao()); }
    public CasosService(CasoJdbcDao dao) { this.dao = dao; }

    public Pagina<CasoMunicipal> listar(Identidad u, String codigo, String tipo, String prioridad, String estado,
            Long area, String desde, String hasta, int pagina) throws SQLException {
        Validacion.permiso(u, "RECLAMOS_CONSULTAR");
        var intervalo = Validacion.intervalo(desde, hasta);
        return dao.listar(u, new Filtro(Validacion.texto(codigo, "código", 40, false) == null ? "" : codigo,
                Validacion.catalogo(TIPOS, tipo, "tipo", false),
                PRIORIDAD_URGENTE.equals(prioridad) ? prioridad : Validacion.catalogo(PRIORIDADES, prioridad, "prioridad", false),
                "ABIERTOS".equals(estado) ? estado : Validacion.catalogo(ESTADOS, estado, "estado", false),
                area, intervalo.desde(), intervalo.hastaExclusiva()), pagina);
    }

    public CasoMunicipal detalle(Identidad u, long id) throws SQLException {
        Validacion.permiso(u, "RECLAMOS_CONSULTAR");
        return dao.buscar(u, id).orElseThrow(() -> new IllegalArgumentException("El caso no está disponible para tu área."));
    }

    public long registrar(Identidad u, String tipo, String prioridad, long area, Long persona, String descripcion, String ubicacion)
            throws SQLException {
        Validacion.permiso(u, "RECLAMOS_REGISTRAR");
        return dao.registrar(u, new Alta(Validacion.codigo("CAS"), Validacion.catalogo(TIPOS, tipo, "tipo", true),
                Validacion.catalogo(PRIORIDADES, prioridad, "prioridad", true), area, persona,
                Validacion.texto(descripcion, "descripción", 4000, true), Validacion.texto(ubicacion, "ubicación", 220, false)));
    }

    public void atender(Identidad u, long id, long area, String estado, String prioridad, long areaAnterior,
            String estadoAnterior, String prioridadAnterior) throws SQLException {
        Validacion.permiso(u, "RECLAMOS_ATENDER");
        dao.atender(u, id, area, Validacion.catalogo(ESTADOS, estado, "estado", true),
                Validacion.catalogo(PRIORIDADES, prioridad, "prioridad", true), areaAnterior, estadoAnterior, prioridadAnterior);
    }

    public List<Opcion> areas(Identidad u) throws SQLException {
        Validacion.permiso(u, "RECLAMOS_CONSULTAR");
        return dao.areas(u);
    }

    public List<Opcion> contribuyentes(Identidad u, String texto) throws SQLException {
        Validacion.permiso(u, "RECLAMOS_REGISTRAR");
        Validacion.permiso(u, "CONTRIBUYENTES_CONSULTAR");
        return dao.contribuyentes(texto);
    }
}
