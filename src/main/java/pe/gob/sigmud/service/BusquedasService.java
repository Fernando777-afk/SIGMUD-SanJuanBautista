package pe.gob.sigmud.service;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import pe.gob.sigmud.dao.impl.BusquedaJdbcDao;
import pe.gob.sigmud.dao.impl.BusquedaJdbcDao.Orden;
import pe.gob.sigmud.dao.impl.BusquedaJdbcDao.Resultado;
import pe.gob.sigmud.dao.impl.BusquedaJdbcDao.Tipo;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla.Opcion;
import pe.gob.sigmud.util.Validacion;

public final class BusquedasService {
    private final BusquedaJdbcDao dao = new BusquedaJdbcDao();

    public List<Tipo> tipos(Identidad usuario) {
        return Arrays.stream(Tipo.values()).filter(tipo -> tipo.modulo().permitido(usuario)).toList();
    }

    public Pagina<Resultado> buscar(Identidad u, Tipo tipo, String texto, Long area, Orden orden, int pagina)
            throws SQLException {
        List<Tipo> tipos = tipos(u);
        if (tipo != null && !tipos.contains(tipo)) throw new SecurityException("Tipo de búsqueda no autorizado.");
        Validacion.texto(texto, "búsqueda", 180, true);
        if (texto.length() < 2) throw new IllegalArgumentException("Escribe al menos dos caracteres.");
        return dao.buscar(u, tipo == null ? tipos : List.of(tipo), texto, area, orden, pagina);
    }

    public List<Opcion> areas(Identidad u) throws SQLException {
        if (tipos(u).isEmpty()) return List.of();
        return dao.areas(u);
    }
}
