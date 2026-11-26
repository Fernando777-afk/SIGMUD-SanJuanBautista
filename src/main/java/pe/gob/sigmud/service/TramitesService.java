package pe.gob.sigmud.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import pe.gob.sigmud.dao.impl.TramiteJdbcDao;
import pe.gob.sigmud.dao.impl.TramiteJdbcDao.Detalle;
import pe.gob.sigmud.dao.impl.TramiteJdbcDao.Filtros;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla.Opcion;
import pe.gob.sigmud.model.Tramite;
import pe.gob.sigmud.model.Tramite.Estado;
import pe.gob.sigmud.util.Validacion;

public final class TramitesService {
    private final TramiteJdbcDao dao;

    public TramitesService() { this(new TramiteJdbcDao()); }
    public TramitesService(TramiteJdbcDao dao) { this.dao = dao; }

    public Pagina<Tramite> listar(Identidad usuario, String codigo, Long contribuyente, Long area, String estado,
            String desde, String hasta, int pagina) throws SQLException {
        Validacion.permiso(usuario, "TRAMITES_CONSULTAR");
        LocalDate inicio = Validacion.fecha(desde, "desde");
        LocalDate fin = Validacion.fecha(hasta, "hasta");
        if (inicio != null && fin != null && fin.isBefore(inicio)) {
            throw new IllegalArgumentException("La fecha final debe ser igual o posterior a la inicial.");
        }
        return dao.listar(usuario, new Filtros(codigo, contribuyente, area,
                estado.isBlank() ? null : Validacion.opcion(Estado.class, estado, "estado"), inicio,
                fin == null || fin.equals(LocalDate.of(9999, 12, 31)) ? null : fin.plusDays(1)), pagina);
    }

    public Detalle consultar(Identidad usuario, long id) throws SQLException {
        Validacion.permiso(usuario, "TRAMITES_CONSULTAR");
        return dao.detalle(usuario, id).orElseThrow(() -> new IllegalArgumentException("El expediente no está disponible para tu área."));
    }

    public List<Detalle> historial(Identidad usuario, long id) throws SQLException {
        Validacion.permiso(usuario, "TRAMITES_CONSULTAR");
        return dao.historialContribuyente(usuario, id);
    }

    public List<Opcion> areas(Identidad usuario, boolean autorizadas) throws SQLException {
        Validacion.permiso(usuario, "TRAMITES_CONSULTAR");
        return dao.areas(usuario, autorizadas);
    }

    public List<Opcion> contribuyentes(Identidad usuario, String texto, Long seleccionado) throws SQLException {
        Validacion.permiso(usuario, "TRAMITES_REGISTRAR");
        return dao.contribuyentes(texto, seleccionado);
    }

    public List<Opcion> tipos(Identidad usuario) throws SQLException {
        Validacion.permiso(usuario, "TRAMITES_REGISTRAR");
        Validacion.permiso(usuario, "TRAMITES_CONSULTAR");
        return dao.tipos(usuario);
    }

    public String registrar(Identidad usuario, long persona, long area, String tipo, String descripcion) throws SQLException {
        Validacion.permiso(usuario, "TRAMITES_REGISTRAR");
        String codigo = "EXP-" + LocalDate.now(ZoneId.of("America/Lima")).getYear() + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(java.util.Locale.ROOT);
        dao.registrar(usuario.id(), codigo, persona, area,
                Validacion.texto(tipo, "tipo de trámite", 120, true),
                Validacion.texto(descripcion, "descripción", 4000, true));
        return codigo;
    }

    public void actualizar(Identidad usuario, long id, long version, long area, String estado, String observacion)
            throws SQLException {
        Validacion.permiso(usuario, "TRAMITES_ACTUALIZAR");
        dao.actualizar(usuario, id, version, area, Validacion.opcion(Estado.class, estado, "estado"),
                Validacion.texto(observacion, "observación", 500, false));
    }
}
