package pe.gob.sigmud.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import pe.gob.sigmud.dao.impl.UsuarioJdbcDao;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.util.Contrasenas;

public final class ComprobarAccesoJdbc {

    private static final char[] CLAVE = UUID.randomUUID().toString().toCharArray();
    private static final char[] INCORRECTA = UUID.randomUUID().toString().toCharArray();
    private static final String HASH = Contrasenas.generar(CLAVE);

    private final Connection mantenerBase;
    private final AutenticacionService autenticacion;
    private final UsuarioJdbcDao dao;

    private ComprobarAccesoJdbc(JdbcDataSource fuente, Connection conexion) throws SQLException {
        mantenerBase = conexion;
        dao = new UsuarioJdbcDao(fuente::getConnection);
        autenticacion = new AutenticacionService(dao);
        crearTablas(mantenerBase);
        insertar("demo.admin", "Admin de prueba", 1, true, HASH);
        insertar("demo.mesa", "Mesa de prueba", 2, true, HASH);
        insertar("demo.rentas", "Cuenta inactiva", 2, false, null);
    }

    public static void main(String[] args) throws Exception {
        probar("login, roles y permisos", ComprobarAccesoJdbc::autenticaYCargaRolesYPermisosRealesDeLasTablas);
        probar("credencial incorrecta, cuenta inactiva y reinicio del contador",
                ComprobarAccesoJdbc::rechazaCredencialIncorrectaEInactivaYReiniciaElContadorAlAutenticar);
        probar("bloqueo y vencimiento", ComprobarAccesoJdbc::bloqueaTrasCincoIntentosYAbreOtroCicloAlVencer);
        probar("creación, baja y revocación", ComprobarAccesoJdbc::administradorCreaYDeshabilitaUnaCuentaConHashYRevocaSuSesion);
        probar("restablecimiento y revocación", ComprobarAccesoJdbc::restablecerContrasenaRevocaLaSesionAnterior);
        probar("derivación, sal y hash inválido", ComprobarAccesoJdbc::usaUnaSalDistintaYRechazaUnHashInvalido);
    }

    private static void probar(String nombre, Caso caso) throws Exception {
        JdbcDataSource fuente = new JdbcDataSource();
        fuente.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        try (Connection conexion = fuente.getConnection()) {
            caso.ejecutar(new ComprobarAccesoJdbc(fuente, conexion));
        }
        System.out.println("OK " + nombre);
    }

    void autenticaYCargaRolesYPermisosRealesDeLasTablas() throws SQLException {
        Identidad administrador = autenticacion.consultarSesion(
                autenticacion.autenticar("demo.admin", CLAVE).orElseThrow()).orElseThrow();
        Identidad operador = autenticacion.consultarSesion(
                autenticacion.autenticar("demo.mesa", CLAVE).orElseThrow()).orElseThrow();

        assertTrue(administrador.administraUsuarios());
        assertFalse(operador.administraUsuarios());
        assertEquals("MESA_PARTES", operador.codigoRol());
        assertTrue(operador.permisos().contains("TRAMITES_REGISTRAR"));
    }

    void rechazaCredencialIncorrectaEInactivaYReiniciaElContadorAlAutenticar() throws SQLException {
        assertTrue(autenticacion.autenticar("demo.mesa", INCORRECTA).isEmpty());
        assertEquals(1, dao.buscarCredencial("demo.mesa").orElseThrow().intentos());
        assertTrue(autenticacion.autenticar("demo.rentas", CLAVE).isEmpty());
        assertEquals(0, dao.buscarCredencial("demo.rentas").orElseThrow().intentos());
        assertTrue(autenticacion.autenticar("cuenta-inexistente", CLAVE).isEmpty());
        assertTrue(autenticacion.autenticar("demo.mesa", CLAVE).isPresent());
        assertEquals(0, dao.buscarCredencial("demo.mesa").orElseThrow().intentos());
    }

    void bloqueaTrasCincoIntentosYAbreOtroCicloAlVencer() throws SQLException {
        for (int i = 0; i < 5; i++) {
            assertTrue(autenticacion.autenticar("demo.mesa", INCORRECTA).isEmpty());
        }
        assertTrue(dao.buscarCredencial("demo.mesa").orElseThrow().bloqueada());
        assertTrue(autenticacion.autenticar("demo.mesa", CLAVE).isEmpty());

        try (Statement sql = mantenerBase.createStatement()) {
            sql.executeUpdate("UPDATE usuarios SET bloqueado_hasta = TIMESTAMP '2000-01-01 00:00:00' "
                    + "WHERE nombre_usuario = 'demo.mesa'");
        }
        assertTrue(autenticacion.autenticar("demo.mesa", INCORRECTA).isEmpty());
        assertEquals(1, dao.buscarCredencial("demo.mesa").orElseThrow().intentos());
        assertFalse(dao.buscarCredencial("demo.mesa").orElseThrow().bloqueada());
    }

    void administradorCreaYDeshabilitaUnaCuentaConHashYRevocaSuSesion() throws SQLException {
        UsuariosService usuarios = new UsuariosService(dao);
        Identidad admin = autenticacion.consultarSesion(
                autenticacion.autenticar("demo.admin", CLAVE).orElseThrow()).orElseThrow();
        usuarios.guardar(admin, 0, "cuenta.prueba", "Cuenta de prueba", 2, true, CLAVE, false, null);
        var acceso = autenticacion.autenticar("cuenta.prueba", CLAVE).orElseThrow();
        Identidad operador = autenticacion.consultarSesion(acceso).orElseThrow();

        assertNotEquals(new String(CLAVE), dao.buscarCredencial("cuenta.prueba").orElseThrow().hash());
        assertThrows(SecurityException.class, () -> usuarios.guardar(operador, 0, "no.permitida",
                "Cuenta de prueba", 1, true, CLAVE, false, null));
        assertThrows(IllegalArgumentException.class, () -> usuarios.guardar(admin, admin.id(), "",
                "Admin de prueba", 2, true, new char[0], false, null));
        usuarios.guardar(admin, operador.id(), "", "Cuenta de prueba", 2, false, new char[0], false, null);
        assertTrue(autenticacion.consultarSesion(acceso).isEmpty());
        assertTrue(autenticacion.autenticar("cuenta.prueba", CLAVE).isEmpty());
    }

    void restablecerContrasenaRevocaLaSesionAnterior() throws SQLException {
        UsuariosService usuarios = new UsuariosService(dao);
        Identidad admin = autenticacion.consultarSesion(
                autenticacion.autenticar("demo.admin", CLAVE).orElseThrow()).orElseThrow();
        var accesoAnterior = autenticacion.autenticar("demo.mesa", CLAVE).orElseThrow();

        usuarios.guardar(admin, accesoAnterior.usuarioId(), "", "Mesa de prueba", 2, true, INCORRECTA, false, null);

        assertTrue(autenticacion.consultarSesion(accesoAnterior).isEmpty());
        assertTrue(autenticacion.autenticar("demo.mesa", CLAVE).isEmpty());
        assertTrue(autenticacion.autenticar("demo.mesa", INCORRECTA).isPresent());
    }

    void usaUnaSalDistintaYRechazaUnHashInvalido() {
        assertTrue(Contrasenas.verificar(CLAVE, HASH));
        assertNotEquals(HASH, Contrasenas.generar(CLAVE));
        assertFalse(Contrasenas.verificar(CLAVE, "hash inválido"));
    }

    private void insertar(String usuario, String nombre, int rol, boolean activo, String hash) throws SQLException {
        try (PreparedStatement sql = mantenerBase.prepareStatement("""
                INSERT INTO usuarios (nombre_usuario, nombre_visible, rol_id, activo, hash_contrasena)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            sql.setString(1, usuario);
            sql.setString(2, nombre);
            sql.setInt(3, rol);
            sql.setBoolean(4, activo);
            sql.setString(5, hash);
            sql.executeUpdate();
        }
    }

    private static void crearTablas(Connection conexion) throws SQLException {
        try (Statement sql = conexion.createStatement()) {
            sql.execute("CREATE TABLE areas (id INT PRIMARY KEY, nombre VARCHAR(100), baja_en TIMESTAMP)");
            sql.execute("CREATE TABLE roles (id INT PRIMARY KEY, codigo VARCHAR(40), nombre VARCHAR(100))");
            sql.execute("CREATE TABLE permisos (id INT PRIMARY KEY, codigo VARCHAR(45))");
            sql.execute("""
                    CREATE TABLE roles_permisos (
                        rol_id INT REFERENCES roles(id), permiso_id INT REFERENCES permisos(id),
                        PRIMARY KEY (rol_id, permiso_id)
                    )
                    """);
            sql.execute("""
                    CREATE TABLE usuarios (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY, nombre_usuario VARCHAR(64) NOT NULL UNIQUE,
                        nombre_visible VARCHAR(160) NOT NULL, rol_id INT NOT NULL REFERENCES roles(id), area_id INT,
                        activo TINYINT NOT NULL DEFAULT 0, hash_contrasena VARCHAR(255),
                        intentos_fallidos SMALLINT NOT NULL DEFAULT 0, bloqueado_hasta TIMESTAMP(6),
                        CHECK (activo = 0 OR hash_contrasena IS NOT NULL)
                    )
                    """);
            sql.execute("INSERT INTO roles VALUES (1, 'ADMINISTRADOR', 'Administrador'), (2, 'MESA_PARTES', 'Mesa de Partes')");
            sql.execute("INSERT INTO permisos VALUES (1, 'SEGURIDAD_ADMINISTRAR'), (2, 'TRAMITES_REGISTRAR')");
            sql.execute("INSERT INTO roles_permisos VALUES (1, 1), (2, 2)");
        }
    }

    private static void assertTrue(boolean condicion) {
        if (!condicion) {
            throw new AssertionError("La condición de acceso no se cumplió.");
        }
    }

    private static void assertFalse(boolean condicion) {
        assertTrue(!condicion);
    }

    private static void assertEquals(Object esperado, Object resultado) {
        assertTrue(esperado.equals(resultado));
    }

    private static void assertNotEquals(Object esperado, Object resultado) {
        assertFalse(esperado.equals(resultado));
    }

    private static void assertThrows(Class<? extends RuntimeException> esperada, Accion accion) throws SQLException {
        try {
            accion.ejecutar();
        } catch (RuntimeException e) {
            assertTrue(esperada.isInstance(e));
            return;
        }
        throw new AssertionError("El control de acceso debía rechazar la operación.");
    }

    @FunctionalInterface
    private interface Caso {
        void ejecutar(ComprobarAccesoJdbc prueba) throws SQLException;
    }

    @FunctionalInterface
    private interface Accion {
        void ejecutar() throws SQLException;
    }
}
