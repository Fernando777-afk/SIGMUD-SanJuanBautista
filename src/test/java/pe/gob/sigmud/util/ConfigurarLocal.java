package pe.gob.sigmud.util;

import java.io.Console;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;

/** Asistente de consola local. Solo comprueba el catálogo y prepara archivos privados. */
public final class ConfigurarLocal {
    private ConfigurarLocal() {
    }

    public static void main(String[] args) {
        Console consola = System.console();
        boolean desdeEntorno = args.length == 2 && "--desde-entorno".equals(args[1]);
        if (!desdeEntorno && (args.length != 1 || consola == null)) {
            System.err.println("Abre Configurar-SIGMUD.cmd en una consola interactiva.");
            System.exit(1);
        }
        try {
            Path proyecto = Path.of(args[0]).toAbsolutePath().normalize();
            if (desdeEntorno) {
                Properties valores = new Properties();
                for (String nombre : ConexionBD.CLAVES) {
                    valores.setProperty(nombre, System.getenv().getOrDefault(nombre, ""));
                }
                guardar(proyecto.resolve("config"), valores);
            } else {
                configurar(consola, proyecto);
            }
        } catch (IOException | SQLException | IllegalArgumentException e) {
            System.err.println(e instanceof SQLException ? "No se validó la conexión. Revisa servidor, TLS, cuenta y permiso SELECT sobre roles."
                    : "No se completó la configuración. Revisa la plantilla y los permisos de escritura.");
            System.exit(1);
        }
    }

    private static void configurar(Console consola, Path proyecto) throws IOException, SQLException {
        Path directorio = proyecto.resolve("config");
        Properties valores = new Properties();
        try (var entrada = Files.newBufferedReader(directorio.resolve("sigmud-local.example.properties"))) {
            valores.load(entrada);
        }
        for (String clave : List.of("SIGMUD_DB_URL", "SIGMUD_DB_USER")) {
            String valor = consola.readLine("%s [%s]: ", clave, valores.getProperty(clave));
            if (valor == null) {
                throw new IOException("Entrada cancelada");
            }
            if (!valor.isBlank()) {
                valores.setProperty(clave, valor.strip());
            }
        }
        char[] clave = consola.readPassword("Contraseña MySQL (entrada oculta): ");
        if (clave == null) {
            throw new IOException("Entrada cancelada");
        }
        try {
            valores.setProperty("SIGMUD_DB_PASSWORD", new String(clave));
            guardar(directorio, valores);
        } finally {
            Arrays.fill(clave, '\0');
            valores.remove("SIGMUD_DB_PASSWORD");
        }
    }

    private static void guardar(Path directorio, Properties valores) throws IOException, SQLException {
        Path destino = directorio.resolve("sigmud-local.properties");
        try (Connection c = ConexionBD.abrir(valores, "asistente");
             PreparedStatement sql = c.prepareStatement("SELECT COUNT(*) FROM roles");
             ResultSet filas = sql.executeQuery()) {
            filas.next();
            System.out.printf("Conexión verificada. Roles consultados: %d.%n", filas.getInt(1));
        }
        Path temporal = Files.createTempFile(directorio, ".jdbc-", ".tmp");
        try {
            restringir(temporal);
            try (Writer salida = Files.newBufferedWriter(temporal)) {
                valores.store(salida, "Configuración privada de SIGMUD");
            }
            publicar(temporal, destino);
        } finally {
            Files.deleteIfExists(temporal);
        }
        Path puntero = ConexionBD.localizador();
        Files.createDirectories(puntero.getParent());
        Path rutaTemporal = Files.createTempFile(puntero.getParent(), "ruta-", ".tmp");
        try {
            restringir(rutaTemporal);
            Files.writeString(rutaTemporal, destino.toString());
            publicar(rutaTemporal, puntero);
        } finally {
            Files.deleteIfExists(rutaTemporal);
        }
        System.out.println("Configuración guardada. Abre NetBeans desde su icono y ejecuta SIGMUD con F6.");
    }

    private static void publicar(Path temporal, Path destino) throws IOException {
        Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void restringir(Path archivo) throws IOException {
        AclFileAttributeView permisos = Files.getFileAttributeView(archivo, AclFileAttributeView.class);
        if (permisos == null) {
            Files.setPosixFilePermissions(archivo, PosixFilePermissions.fromString("rw-------"));
            return;
        }
        var propietario = archivo.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
        permisos.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(propietario)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
    }
}
