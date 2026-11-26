package pe.gob.sigmud.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import pe.gob.sigmud.dao.Jdbc;
import pe.gob.sigmud.dao.AccesoDao;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.util.ConexionBD;
import pe.gob.sigmud.util.Contrasenas;

public final class UsuarioJdbcDao implements AccesoDao {

    private static final String CREDENCIALES = """
            SELECT id, nombre_usuario, hash_contrasena, activo, intentos_fallidos,
                   bloqueado_hasta > CURRENT_TIMESTAMP(6) AS bloqueado,
                   bloqueado_hasta <= CURRENT_TIMESTAMP(6) AS ciclo_vencido
            FROM usuarios
            """;
    private static final String CUENTAS = """
            SELECT u.id, u.nombre_usuario, u.nombre_visible, u.rol_id, r.nombre AS rol, u.activo,
                   u.area_id, a.nombre AS area,
                   u.hash_contrasena IS NOT NULL AS tiene_clave,
                   u.bloqueado_hasta > CURRENT_TIMESTAMP(6) AS bloqueado
            FROM usuarios u JOIN roles r ON r.id = u.rol_id LEFT JOIN areas a ON a.id = u.area_id
            """;
    private final ProveedorConexion proveedor;

    @FunctionalInterface
    public interface ProveedorConexion {
        Connection abrir() throws SQLException;
    }

    public record Cuenta(long id, String usuario, String nombreVisible, int rolId, String rol,
            boolean activa, boolean tieneClave, boolean bloqueada, Integer areaId, String area) {
        public boolean identificadorReservado() {
            return usuario.toLowerCase(java.util.Locale.ROOT).startsWith("demo.");
        }
    }

    public record Rol(int id, String nombre) {
    }

    public record Area(int id, String nombre) {}

    public record Permiso(String rol, String codigo, String descripcion) {}

    private record CuentaBloqueada(String usuario, boolean tieneClave) {}

    public record DatosCuenta(long id, String usuario, String nombreVisible, int rolId,
            boolean activa, String nuevoHash, boolean desbloquear, Integer areaId) {
    }

    public UsuarioJdbcDao() {
        this(ConexionBD::abrir);
    }

    public UsuarioJdbcDao(ProveedorConexion proveedor) {
        this.proveedor = proveedor;
    }

    @Override
    public Optional<Credencial> buscarCredencial(String usuario) throws SQLException {
        try (Connection conexion = proveedor.abrir();
                PreparedStatement sql = conexion.prepareStatement(CREDENCIALES + " WHERE nombre_usuario = ?")) {
            sql.setString(1, usuario);
            return Optional.ofNullable(leerCredencial(sql));
        }
    }

    @Override
    public boolean registrarIntento(Credencial comprobada, boolean correcta, int maxIntentos,
            int minutosBloqueo) throws SQLException {
        return enTransaccion(conexion -> {
            Credencial actual;
            try (PreparedStatement sql = conexion.prepareStatement(CREDENCIALES + " WHERE id = ? FOR UPDATE")) {
                sql.setLong(1, comprobada.id());
                actual = leerCredencial(sql);
            }
            if (actual == null || !actual.activa() || actual.bloqueada()
                    || !Objects.equals(actual.hash(), comprobada.hash())) {
                return false;
            }
            int anteriores = actual.cicloVencido() ? 0 : actual.intentos();
            int intentos = correcta ? 0 : Math.min(anteriores + 1, maxIntentos);
            try (PreparedStatement sql = conexion.prepareStatement("""
                    UPDATE usuarios SET intentos_fallidos = ?,
                        bloqueado_hasta = CASE WHEN ? THEN TIMESTAMPADD(MINUTE, ?, CURRENT_TIMESTAMP(6)) ELSE NULL END
                    WHERE id = ?
                    """)) {
                sql.setInt(1, intentos);
                sql.setBoolean(2, intentos == maxIntentos);
                sql.setInt(3, minutosBloqueo);
                sql.setLong(4, comprobada.id());
                sql.executeUpdate();
            }
            return correcta;
        });
    }

    @Override
    public Optional<Identidad> buscarIdentidad(long id, String huella) throws SQLException {
        try (Connection conexion = proveedor.abrir();
                PreparedStatement sql = conexion.prepareStatement("""
                        SELECT u.id, u.nombre_usuario, u.nombre_visible, u.hash_contrasena, u.rol_id,
                               r.codigo AS codigo_rol, r.nombre AS rol, p.codigo AS permiso
                        FROM usuarios u JOIN roles r ON r.id = u.rol_id
                        LEFT JOIN roles_permisos rp ON rp.rol_id = r.id
                        LEFT JOIN permisos p ON p.id = rp.permiso_id
                        WHERE u.id = ? AND u.activo = 1 AND u.hash_contrasena IS NOT NULL
                        """)) {
            sql.setLong(1, id);
            try (ResultSet fila = sql.executeQuery()) {
                if (!fila.next() || !Contrasenas.huellaSesion(fila.getString("hash_contrasena")).equals(huella)) {
                    return Optional.empty();
                }
                String usuario = fila.getString("nombre_usuario");
                String nombre = fila.getString("nombre_visible");
                int rolId = fila.getInt("rol_id");
                String codigoRol = fila.getString("codigo_rol");
                String rol = fila.getString("rol");
                Set<String> permisos = new HashSet<>();
                do {
                    String permiso = fila.getString("permiso");
                    if (permiso != null) {
                        permisos.add(permiso);
                    }
                } while (fila.next());
                return Optional.of(new Identidad(id, usuario, nombre, rolId, codigoRol, rol, permisos));
            }
        }
    }

    public Pagina<Cuenta> listarCuentas(String texto, Integer rol, String estado, int pagina) throws SQLException {
        String condicionEstado = switch (estado) {
            case "activa" -> " AND u.activo = 1 AND (u.bloqueado_hasta IS NULL OR u.bloqueado_hasta <= CURRENT_TIMESTAMP(6))";
            case "inactiva" -> " AND u.activo = 0";
            case "bloqueada" -> " AND u.activo = 1 AND u.bloqueado_hasta > CURRENT_TIMESTAMP(6)";
            default -> "";
        };
        String consulta = CUENTAS + """
                WHERE (u.nombre_usuario LIKE ? ESCAPE '!' OR u.nombre_visible LIKE ? ESCAPE '!'
                       OR r.nombre LIKE ? ESCAPE '!')
                    AND (? IS NULL OR u.rol_id = ?)
                """ + condicionEstado + " ORDER BY u.nombre_usuario, u.id LIMIT ? OFFSET ?";
        String patron = Jdbc.contiene(texto);
        try (Connection conexion = proveedor.abrir()) {
            return Pagina.desde(Jdbc.listar(conexion, consulta, UsuarioJdbcDao::leerCuenta,
                    patron, patron, patron, rol, rol, Pagina.TAMANO + 1, Pagina.desplazamiento(pagina)), pagina);
        }
    }

    public Optional<Cuenta> buscarCuenta(long id) throws SQLException {
        try (Connection conexion = proveedor.abrir();
                PreparedStatement sql = conexion.prepareStatement(CUENTAS + " WHERE u.id = ?")) {
            sql.setLong(1, id);
            try (ResultSet fila = sql.executeQuery()) {
                return fila.next() ? Optional.of(leerCuenta(fila)) : Optional.empty();
            }
        }
    }

    public List<Rol> listarRoles() throws SQLException {
        try (Connection conexion = proveedor.abrir();
                PreparedStatement sql = conexion.prepareStatement("SELECT id, nombre FROM roles ORDER BY nombre");
                ResultSet filas = sql.executeQuery()) {
            List<Rol> roles = new ArrayList<>();
            while (filas.next()) {
                roles.add(new Rol(filas.getInt("id"), filas.getString("nombre")));
            }
            return roles;
        }
    }

    public void guardar(Identidad operador, DatosCuenta datos) throws SQLException {
        enTransaccion(conexion -> {
            int rolActual = bloquearAdministrador(conexion, operador.id());
            if (datos.id() == operador.id() && (!datos.activa() || datos.rolId() != rolActual)) {
                throw new IllegalArgumentException("Conserva la habilitación y el rol de tu propia cuenta.");
            }
            if (datos.id() == 0) {
                insertarCuenta(conexion, datos);
            } else {
                CuentaBloqueada actual = Jdbc.listar(conexion, """
                        SELECT nombre_usuario, hash_contrasena IS NOT NULL AS tiene_clave
                        FROM usuarios WHERE id = ? FOR UPDATE
                        """, fila -> new CuentaBloqueada(fila.getString("nombre_usuario"),
                        fila.getBoolean("tiene_clave")), datos.id()).stream().findFirst().orElseThrow(
                        () -> new IllegalArgumentException("La cuenta ya no está disponible."));
                validarCambios(actual, datos);
                cambiarCuenta(conexion, datos, actual.usuario());
            }
            return null;
        });
    }

    public List<Area> listarAreas() throws SQLException {
        try (Connection conexion = proveedor.abrir()) {
            return Jdbc.listar(conexion, "SELECT id, nombre FROM areas WHERE baja_en IS NULL ORDER BY nombre",
                    fila -> new Area(fila.getInt("id"), fila.getString("nombre")));
        }
    }

    public List<Permiso> listarPermisos() throws SQLException {
        try (Connection conexion = proveedor.abrir()) {
            return Jdbc.listar(conexion, """
                    SELECT r.nombre AS rol, p.codigo, p.descripcion
                    FROM roles r JOIN roles_permisos rp ON rp.rol_id = r.id
                    JOIN permisos p ON p.id = rp.permiso_id ORDER BY r.nombre, p.codigo
                    """, fila -> new Permiso(fila.getString("rol"), fila.getString("codigo"),
                    fila.getString("descripcion")));
        }
    }

    public boolean inicializarDemo(String usuario, String hash) throws SQLException {
        try (Connection conexion = proveedor.abrir();
                PreparedStatement sql = conexion.prepareStatement("""
                        UPDATE usuarios
                        SET hash_contrasena = ?, activo = 1, intentos_fallidos = 0, bloqueado_hasta = NULL
                        WHERE nombre_usuario = ? AND CAST(nombre_usuario AS BINARY) = CAST(? AS BINARY)
                          AND activo = 0 AND hash_contrasena IS NULL
                        """)) {
            sql.setString(1, hash);
            sql.setString(2, usuario);
            sql.setString(3, usuario);
            return sql.executeUpdate() == 1;
        }
    }

    private static Credencial leerCredencial(PreparedStatement sql) throws SQLException {
        try (ResultSet fila = sql.executeQuery()) {
            return fila.next() ? new Credencial(fila.getLong("id"), fila.getString("nombre_usuario"),
                    fila.getString("hash_contrasena"), fila.getBoolean("activo"), fila.getInt("intentos_fallidos"),
                    fila.getBoolean("bloqueado"), fila.getBoolean("ciclo_vencido")) : null;
        }
    }

    private static Cuenta leerCuenta(ResultSet fila) throws SQLException {
        return new Cuenta(fila.getLong("id"), fila.getString("nombre_usuario"), fila.getString("nombre_visible"),
                fila.getInt("rol_id"), fila.getString("rol"), fila.getBoolean("activo"),
                fila.getBoolean("tiene_clave"), fila.getBoolean("bloqueado"),
                fila.getObject("area_id", Integer.class), fila.getString("area"));
    }

    private static int bloquearAdministrador(Connection conexion, long id) throws SQLException {
        try (PreparedStatement sql = conexion.prepareStatement("""
                SELECT u.rol_id FROM usuarios u
                WHERE u.id = ? AND u.activo = 1 AND EXISTS (
                    SELECT 1 FROM roles r JOIN roles_permisos rp ON rp.rol_id = r.id
                    JOIN permisos p ON p.id = rp.permiso_id WHERE r.id = u.rol_id
                      AND r.codigo = 'ADMINISTRADOR' AND p.codigo = 'SEGURIDAD_ADMINISTRAR'
                ) FOR UPDATE
                """)) {
            sql.setLong(1, id);
            try (ResultSet fila = sql.executeQuery()) {
                if (!fila.next()) {
                    throw new SecurityException("Ya no tienes autorización para gestionar cuentas.");
                }
                return fila.getInt("rol_id");
            }
        }
    }

    private static void insertarCuenta(Connection conexion, DatosCuenta datos) throws SQLException {
        try (PreparedStatement sql = conexion.prepareStatement("""
                INSERT INTO usuarios (rol_id, nombre_usuario, nombre_visible, hash_contrasena, activo, area_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            sql.setInt(1, datos.rolId());
            sql.setString(2, datos.usuario());
            sql.setString(3, datos.nombreVisible());
            sql.setString(4, datos.nuevoHash());
            sql.setBoolean(5, datos.activa());
            sql.setObject(6, datos.areaId());
            sql.executeUpdate();
        }
    }

    private static void validarCambios(CuentaBloqueada actual, DatosCuenta datos) {
        if (actual.usuario().toLowerCase(java.util.Locale.ROOT).startsWith("demo.")
                && !datos.usuario().isBlank() && !datos.usuario().equals(actual.usuario())) {
            throw new IllegalArgumentException("Esta cuenta conserva su identificador de acceso.");
        }
        if (datos.activa() && !actual.tieneClave() && datos.nuevoHash() == null) {
            throw new IllegalArgumentException("Para habilitar la cuenta, establece primero una contraseña.");
        }
    }

    private static void cambiarCuenta(Connection conexion, DatosCuenta datos, String usuarioActual) throws SQLException {
        boolean restablecer = datos.nuevoHash() != null || datos.desbloquear() || !datos.activa();
        try (PreparedStatement sql = conexion.prepareStatement("""
                UPDATE usuarios SET rol_id = ?, nombre_visible = ?, activo = ?,
                    nombre_usuario = ?, area_id = ?,
                    hash_contrasena = COALESCE(?, hash_contrasena),
                    intentos_fallidos = CASE WHEN ? THEN 0 ELSE intentos_fallidos END,
                    bloqueado_hasta = CASE WHEN ? THEN NULL ELSE bloqueado_hasta END
                WHERE id = ?
                """)) {
            sql.setInt(1, datos.rolId());
            sql.setString(2, datos.nombreVisible());
            sql.setBoolean(3, datos.activa());
            sql.setString(4, datos.usuario().isBlank() ? usuarioActual : datos.usuario());
            sql.setObject(5, datos.areaId());
            sql.setString(6, datos.nuevoHash());
            sql.setBoolean(7, restablecer);
            sql.setBoolean(8, restablecer);
            sql.setLong(9, datos.id());
            if (sql.executeUpdate() == 0) {
                throw new IllegalArgumentException("La cuenta ya no está disponible.");
            }
        }
    }

    private <T> T enTransaccion(Operacion<T> operacion) throws SQLException {
        try (Connection conexion = proveedor.abrir()) {
            conexion.setAutoCommit(false);
            try {
                T resultado = operacion.ejecutar(conexion);
                conexion.commit();
                return resultado;
            } catch (SQLException | RuntimeException e) {
                try {
                    conexion.rollback();
                } catch (SQLException errorAlRevertir) {
                    e.addSuppressed(errorAlRevertir);
                }
                throw e;
            }
        }
    }

    @FunctionalInterface
    private interface Operacion<T> {
        T ejecutar(Connection conexion) throws SQLException;
    }
}
