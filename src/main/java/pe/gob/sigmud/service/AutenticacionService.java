package pe.gob.sigmud.service;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.Optional;
import pe.gob.sigmud.dao.AccesoDao;
import pe.gob.sigmud.dao.AccesoDao.Credencial;
import pe.gob.sigmud.dao.impl.UsuarioJdbcDao;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.util.Contrasenas;

public final class AutenticacionService {

    private static final int MAX_INTENTOS = 5;
    private static final int MINUTOS_BLOQUEO = 15;
    private final AccesoDao dao;

    public record Acceso(long usuarioId, String huella) implements Serializable {
    }

    public AutenticacionService() {
        this(new UsuarioJdbcDao());
    }

    public AutenticacionService(AccesoDao dao) {
        this.dao = dao;
    }

    public Optional<Acceso> autenticar(String usuario, char[] contrasena) throws SQLException {
        Credencial credencial = dao.buscarCredencial(usuario).orElse(null);
        boolean habilitada = credencial != null && credencial.activa() && !credencial.bloqueada();
        boolean correcta = Contrasenas.verificar(contrasena, habilitada ? credencial.hash() : null);
        if (!habilitada || !dao.registrarIntento(credencial, correcta, MAX_INTENTOS, MINUTOS_BLOQUEO)) {
            return Optional.empty();
        }
        return Optional.of(new Acceso(credencial.id(), Contrasenas.huellaSesion(credencial.hash())));
    }

    public Optional<Identidad> consultarSesion(Acceso acceso) throws SQLException {
        return dao.buscarIdentidad(acceso.usuarioId(), acceso.huella());
    }
}
