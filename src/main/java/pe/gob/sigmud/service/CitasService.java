package pe.gob.sigmud.service;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import pe.gob.sigmud.dao.impl.CitaJdbcDao;
import pe.gob.sigmud.dao.impl.CitaJdbcDao.*;
import pe.gob.sigmud.model.CitaMunicipal;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla.Opcion;
import pe.gob.sigmud.util.Validacion;

public final class CitasService {
    public static final List<String> ESTADOS = List.of("PROGRAMADA", "ATENDIDA", "CANCELADA", "NO_ASISTIO");
    public static final List<String> CIERRES = List.of("CANCELADA", "ATENDIDA", "NO_ASISTIO");
    private final CitaJdbcDao dao;

    public CitasService() { this(new CitaJdbcDao()); }
    public CitasService(CitaJdbcDao dao) { this.dao = dao; }

    public Pagina<CitaMunicipal> agenda(Identidad u, String codigo, String estado, Long area, Long funcionario,
            String desde, String hasta, int pagina) throws SQLException {
        Validacion.permiso(u, "CITAS_CONSULTAR");
        var intervalo = Validacion.intervalo(desde, hasta);
        Validacion.texto(codigo, "código", 40, false);
        return dao.agenda(u, new Filtro(codigo, Validacion.catalogo(ESTADOS, estado, "estado", false), area, funcionario,
                intervalo.desde(), intervalo.hastaExclusiva()), pagina);
    }

    public CitaMunicipal detalle(Identidad u, long id) throws SQLException {
        Validacion.permiso(u, "CITAS_CONSULTAR");
        return dao.detalle(u, id).orElseThrow(() -> new IllegalArgumentException("La cita no está disponible para tu área."));
    }

    public boolean disponible(Identidad u, long ventanilla, long funcionario, String inicio, String fin) throws SQLException {
        Validacion.permiso(u, "CITAS_CONSULTAR");
        return dao.disponible(u, horario(ventanilla, funcionario, inicio, fin));
    }

    public long guardar(Identidad u, Long anterior, long persona, long ventanilla, long funcionario,
            String inicio, String fin, String motivo) throws SQLException {
        Validacion.permiso(u, "CITAS_GESTIONAR");
        try {
            return dao.guardar(u, anterior, new Alta(Validacion.codigo("CIT"), persona,
                    horario(ventanilla, funcionario, inicio, fin), Validacion.texto(motivo, "motivo", 240, true)));
        } catch (SQLException e) {
            if (e.getErrorCode() == 1142 || e.getErrorCode() == 1143) {
                throw new IllegalArgumentException("La instalación necesita actualizar los permisos de citas para registrar la reserva.");
            }
            if ("45000".equals(e.getSQLState()) || e.getErrorCode() == 1062) {
                throw new IllegalArgumentException("La ventanilla o el funcionario no están disponibles para ese horario.");
            }
            if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) {
                throw new IllegalArgumentException("Otra reserva se está registrando en paralelo. Revisa la disponibilidad.");
            }
            throw e;
        }
    }

    public void finalizar(Identidad u, long id, String estado) throws SQLException {
        Validacion.permiso(u, "CITAS_GESTIONAR");
        dao.finalizar(u, id, Validacion.catalogo(CIERRES, estado, "estado de cierre", true));
    }

    public List<Opcion> areas(Identidad u) throws SQLException { return dao.areas(u); }
    public List<Opcion> ventanillas(Identidad u) throws SQLException { return dao.ventanillas(u); }
    public List<Opcion> funcionarios(Identidad u, Long ventanilla) throws SQLException {
        return dao.funcionarios(u, ventanilla, true);
    }
    public List<Opcion> funcionariosAgenda(Identidad u) throws SQLException {
        return dao.funcionarios(u, null, false);
    }
    public List<Opcion> contribuyentes(Identidad u, String texto) throws SQLException {
        Validacion.permiso(u, "CITAS_GESTIONAR");
        Validacion.texto(texto, "búsqueda", 180, false);
        return dao.contribuyentes(texto);
    }

    private static Horario horario(long ventanilla, long funcionario, String inicio, String fin) {
        LocalDateTime desde = Validacion.fechaHora(inicio, "inicio"), hasta = Validacion.fechaHora(fin, "fin");
        if (!hasta.isAfter(desde)) throw new IllegalArgumentException("El fin debe ser posterior al inicio.");
        if (desde.isBefore(LocalDateTime.now(ZoneId.of("America/Lima")))) {
            throw new IllegalArgumentException("Selecciona un horario futuro.");
        }
        return new Horario(ventanilla, funcionario, desde, hasta);
    }
}
