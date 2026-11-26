package pe.gob.sigmud.util;

import com.mysql.cj.jdbc.MysqlDataSource;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConexionBD {

    private static final Pattern URL_MYSQL = Pattern.compile(
            "jdbc:mysql://([a-zA-Z0-9.-]+)(?::([0-9]{1,5}))?/sigmud_db"
    );

    static final List<String> CLAVES = List.of("SIGMUD_DB_URL", "SIGMUD_DB_USER", "SIGMUD_DB_PASSWORD");

    static Path localizador() {
        return Path.of(System.getProperty("user.home"), ".sigmud", "config.path");
    }

    private ConexionBD() {
    }

    public static final class ConfiguracionJdbcException extends SQLException {

        private ConfiguracionJdbcException(String mensaje) {
            super(mensaje);
        }
    }

    /**
     * Abre una conexión independiente. Quien la solicita debe cerrarla.
     */
    public static Connection abrir() throws SQLException {
        Properties valores = new Properties();
        boolean usaEntorno = false;
        for (String clave : CLAVES) {
            String valor = System.getenv(clave);
            if (valor != null) {
                usaEntorno = true;
                valores.setProperty(clave, valor);
            }
        }
        if (!usaEntorno) {
            try {
                Path archivo = Path.of(Files.readString(localizador(), StandardCharsets.UTF_8).strip());
                if (!archivo.isAbsolute()) {
                    throw new ConfiguracionJdbcException("La ubicación del archivo JDBC debe ser absoluta.");
                }
                try (Reader lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
                    valores.load(lector);
                }
            } catch (IOException | IllegalArgumentException e) {
                throw new ConfiguracionJdbcException("No se pudo leer la configuración JDBC local. Ejecuta Configurar-SIGMUD.cmd.");
            }
        }
        return abrir(valores, usaEntorno ? "entorno" : "archivo local");
    }

    static Connection abrir(Properties valores, String origen) throws SQLException {
        for (String clave : CLAVES) {
            if (valores.getProperty(clave, "").isBlank()) {
                throw new ConfiguracionJdbcException("Configuración JDBC incompleta en " + origen + ". Falta " + clave + ".");
            }
        }
        String url = valores.getProperty("SIGMUD_DB_URL").strip();
        String usuario = valores.getProperty("SIGMUD_DB_USER").strip();
        String contrasena = valores.getProperty("SIGMUD_DB_PASSWORD");

        if ("root".equalsIgnoreCase(usuario)) {
            throw new ConfiguracionJdbcException("Configura una cuenta MySQL de aplicación en SIGMUD_DB_USER.");
        }

        Matcher destino = URL_MYSQL.matcher(url);
        if (!destino.matches()) {
            throw new ConfiguracionJdbcException(
                    "SIGMUD_DB_URL debe tener el formato jdbc:mysql://servidor:puerto/sigmud_db.");
        }

        String puerto = destino.group(2);
        if (puerto != null && (Integer.parseInt(puerto) == 0 || Integer.parseInt(puerto) > 65535)) {
            throw new ConfiguracionJdbcException("El puerto indicado en SIGMUD_DB_URL no es válido.");
        }

        String servidor = destino.group(1);
        boolean servidorLocal = "127.0.0.1".equals(servidor) || "localhost".equalsIgnoreCase(servidor);

        try {
            MysqlDataSource fuente = new MysqlDataSource();
            fuente.setURL(url);
            fuente.setSslMode(servidorLocal ? "REQUIRED" : "VERIFY_IDENTITY");
            fuente.setAllowPublicKeyRetrieval(false);
            fuente.setConnectTimeout(5_000);
            fuente.setSocketTimeout(10_000);
            return fuente.getConnection(usuario, contrasena);
        } catch (SQLException e) {
            // El mensaje original del controlador puede revelar detalles de la conexión.
            throw new SQLException(
                    "No fue posible conectar con sigmud_db. Revisa la disponibilidad del servidor, la cuenta y TLS.",
                    e.getSQLState(), e.getErrorCode()
            );
        }
    }

}
