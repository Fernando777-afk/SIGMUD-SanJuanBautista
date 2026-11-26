package pe.gob.sigmud.service;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import pe.gob.sigmud.dao.impl.ReportesJdbcDao;
import pe.gob.sigmud.dao.impl.ReportesJdbcDao.Filtros;
import pe.gob.sigmud.dao.impl.ReportesJdbcDao.Reporte;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla.Opcion;
import pe.gob.sigmud.util.Validacion;

public final class ReportesService {
    private final ReportesJdbcDao dao = new ReportesJdbcDao();

    public List<Reporte> autorizados(Identidad usuario) {
        Validacion.permiso(usuario, "REPORTES_CONSULTAR");
        return Arrays.stream(Reporte.values()).filter(r -> r.modulo().permitido(usuario)).toList();
    }

    public Pagina<List<String>> listar(Identidad u, Reporte r, Filtros f, int p) throws SQLException {
        exigir(u, r);
        return dao.listar(r, u, f, p);
    }

    public List<List<String>> exportar(Identidad u, Reporte r, Filtros f) throws SQLException {
        exigir(u, r);
        return dao.exportar(r, u, f);
    }

    public List<Opcion> areas(Identidad u, Reporte r) throws SQLException {
        exigir(u, r);
        return dao.areas(r, u);
    }

    public Filtros filtros(Reporte r, String desde, String hasta, String area, String estado) {
        var intervalo = r.fechas() ? Validacion.intervalo(desde, hasta) : new Validacion.Intervalo(null, null);
        return new Filtros(intervalo.desde(), intervalo.hastaExclusiva(),
                r.areas() ? Validacion.idOpcional(area, "área") : null,
                r.estados().isEmpty() ? null : Validacion.catalogo(r.estados(), estado, "estado", false));
    }

    private void exigir(Identidad u, Reporte r) {
        if (!autorizados(u).contains(r)) throw new SecurityException("Reporte no autorizado.");
    }
}
