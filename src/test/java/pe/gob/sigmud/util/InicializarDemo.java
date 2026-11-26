package pe.gob.sigmud.util;

import java.io.Console;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import pe.gob.sigmud.dao.impl.UsuarioJdbcDao;

public final class InicializarDemo {

    private static final List<String> USUARIOS = List.of("demo.admin", "demo.mesa", "demo.rentas", "demo.defensa",
            "demo.atencion", "demo.mercados", "demo.almacen", "demo.registro");

    private InicializarDemo() {
    }

    public static void main(String[] args) {
        if (!inicializar(args)) {
            System.exit(1);
        }
    }

    private static boolean inicializar(String[] args) {
        if (args.length != 1 || !(USUARIOS.contains(args[0]) || "--todas".equals(args[0]))) {
            System.err.println("Indica una cuenta demo o --todas para la instalación inicial.");
            return false;
        }
        Console consola = System.console();
        if (consola == null) {
            System.err.println("Ejecuta este comando en una terminal interactiva para ocultar la contraseña.");
            return false;
        }

        UsuarioJdbcDao usuarios = new UsuarioJdbcDao();
        try {
            for (String nombre : "--todas".equals(args[0]) ? USUARIOS : List.of(args[0])) {
                var cuenta = usuarios.buscarCredencial(nombre);
                if (cuenta.isEmpty()) {
                    System.err.println("Falta una cuenta demo. Comprueba la carga inicial: " + nombre);
                    return false;
                }
                if (cuenta.get().activa() || cuenta.get().hash() != null) {
                    consola.printf("%s ya está configurada. Se conserva su estado.%n", nombre);
                    continue;
                }
                if (!habilitar(consola, usuarios, nombre)) {
                    return false;
                }
            }
            return true;
        } catch (SQLException e) {
            System.err.println("No se pudo inicializar la cuenta. Revisa la conexión y los permisos JDBC.");
            return false;
        }
    }

    private static boolean habilitar(Console consola, UsuarioJdbcDao usuarios, String nombre) throws SQLException {
        char[] contrasena = consola.readPassword("Nueva contraseña de %s: ", nombre);
        char[] confirmacion = null;
        try {
            if (contrasena == null || !Contrasenas.longitudValida(contrasena)) {
                System.err.println("Usa una contraseña de 15 a 128 caracteres.");
                return false;
            }
            confirmacion = consola.readPassword("Confirma la contraseña: ");
            if (!Arrays.equals(contrasena, confirmacion)) {
                System.err.println("Las contraseñas no coinciden.");
                return false;
            }
            if (!usuarios.inicializarDemo(nombre, Contrasenas.generar(contrasena))) {
                System.err.println("La cuenta demo debe existir, estar deshabilitada y carecer de contraseña.");
                return false;
            }
            System.out.println("La cuenta demo quedó habilitada.");
            return true;
        } finally {
            if (contrasena != null) {
                Arrays.fill(contrasena, '\0');
            }
            if (confirmacion != null) {
                Arrays.fill(confirmacion, '\0');
            }
        }
    }
}
