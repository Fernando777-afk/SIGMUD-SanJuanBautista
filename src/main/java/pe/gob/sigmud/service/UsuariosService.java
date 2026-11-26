package pe.gob.sigmud.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import pe.gob.sigmud.dao.impl.UsuarioJdbcDao;
import pe.gob.sigmud.dao.impl.UsuarioJdbcDao.Cuenta;
import pe.gob.sigmud.dao.impl.UsuarioJdbcDao.DatosCuenta;
import pe.gob.sigmud.dao.impl.UsuarioJdbcDao.Rol;
import pe.gob.sigmud.dao.impl.UsuarioJdbcDao.Area;
import pe.gob.sigmud.dao.impl.UsuarioJdbcDao.Permiso;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.util.Contrasenas;

public final class UsuariosService {

    private final UsuarioJdbcDao dao;

    public UsuariosService() {
        this(new UsuarioJdbcDao());
    }

    public UsuariosService(UsuarioJdbcDao dao) {
        this.dao = dao;
    }

    public Pagina<Cuenta> listar(String texto, Integer rol, String estado, int pagina) throws SQLException {
        return dao.listarCuentas(texto, rol, estado, pagina);
    }

    public Optional<Cuenta> buscar(long id) throws SQLException {
        return dao.buscarCuenta(id);
    }

    public List<Rol> roles() throws SQLException {
        return dao.listarRoles();
    }

    public List<Area> areas() throws SQLException {
        return dao.listarAreas();
    }

    public List<Permiso> permisos() throws SQLException {
        return dao.listarPermisos();
    }

    public void guardar(Identidad operador, long id, String usuario, String nombreVisible, int rol,
            boolean activa, char[] contrasena, boolean desbloquear, Integer area) throws SQLException {
        if (!operador.administraUsuarios()) {
            throw new SecurityException("No tienes autorización para gestionar cuentas.");
        }
        if ((id == 0 || !usuario.isBlank()) && !usuario.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{2,63}")) {
            throw new IllegalArgumentException("El usuario debe tener de 3 a 64 caracteres con letras, números, puntos o guiones.");
        }
        if (nombreVisible.isBlank() || nombreVisible.length() > 160) {
            throw new IllegalArgumentException("Ingresa un nombre visible de hasta 160 caracteres.");
        }
        if (roles().stream().noneMatch(r -> r.id() == rol)) {
            throw new IllegalArgumentException("Selecciona un rol válido.");
        }

        Cuenta existente = id == 0 ? null : buscar(id).orElseThrow(
                () -> new IllegalArgumentException("La cuenta ya no está disponible."));
        if (usuario.toLowerCase(Locale.ROOT).startsWith("demo.")
                && (existente == null || !usuario.equals(existente.usuario()))) {
            throw new IllegalArgumentException("Selecciona otro identificador de acceso.");
        }
        if (area != null && (existente == null || !area.equals(existente.areaId()))
                && areas().stream().noneMatch(opcion -> opcion.id() == area)) {
            throw new IllegalArgumentException("Selecciona un área municipal disponible.");
        }
        if (activa && contrasena.length == 0 && (existente == null || !existente.tieneClave())) {
            throw new IllegalArgumentException("Para habilitar la cuenta, establece primero una contraseña.");
        }

        String hash = contrasena.length == 0 ? null : Contrasenas.generar(contrasena);
        try {
            dao.guardar(operador, new DatosCuenta(id, usuario, nombreVisible, rol, activa, hash, desbloquear, area));
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062 || "23505".equals(e.getSQLState())) {
                throw new IllegalArgumentException("Ese nombre de usuario ya está registrado.");
            }
            throw e;
        }
    }
}
