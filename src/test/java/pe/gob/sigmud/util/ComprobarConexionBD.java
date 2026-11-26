package pe.gob.sigmud.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Diagnóstico manual de solo lectura para ejecutar desde una terminal local.
 */
public final class ComprobarConexionBD {

    private static final String CONSULTA = """
            SELECT VERSION() AS version_mysql, DATABASE() AS esquema,
                   (SELECT COUNT(*) FROM roles) AS total_roles
            """;

    private ComprobarConexionBD() {
    }

    public static void main(String[] args) {
        if (!verificar()) {
            System.exit(1);
        }
    }

    private static boolean verificar() {
        Connection conexion;
        try {
            conexion = ConexionBD.abrir();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return false;
        }

        try (conexion;
                PreparedStatement consulta = conexion.prepareStatement(CONSULTA);
                ResultSet resultado = consulta.executeQuery()) {
            if (!resultado.next() || !"sigmud_db".equals(resultado.getString("esquema"))) {
                System.err.println("El diagnóstico no pudo confirmar el esquema sigmud_db.");
                return false;
            }

            System.out.println("Conexión JDBC correcta.");
            System.out.println("Versión de MySQL: " + resultado.getString("version_mysql"));
            System.out.println("Esquema seleccionado: " + resultado.getString("esquema"));
            System.out.println("Filas en roles: " + resultado.getLong("total_roles"));
            return true;
        } catch (SQLException e) {
            System.err.println("La conexión se abrió, pero falló la consulta al catálogo roles.");
            return false;
        }
    }
}
