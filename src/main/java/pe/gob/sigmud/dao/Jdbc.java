package pe.gob.sigmud.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import pe.gob.sigmud.util.ConexionBD;
import pe.gob.sigmud.model.Pagina;

public final class Jdbc {
    private Jdbc() {}

    @FunctionalInterface
    public interface Conexion {
        Connection abrir() throws SQLException;
    }

    @FunctionalInterface
    public interface Operacion<T> {
        T ejecutar(Connection conexion) throws SQLException;
    }

    @FunctionalInterface
    public interface Fila<T> {
        T leer(ResultSet fila) throws SQLException;
    }

    public static <T> T leer(Operacion<T> operacion) throws SQLException {
        try (Connection conexion = ConexionBD.abrir()) {
            return operacion.ejecutar(conexion);
        }
    }

    public static <T> T transaccion(Conexion fuente, Operacion<T> operacion) throws SQLException {
        try (Connection conexion = fuente.abrir()) {
            conexion.setAutoCommit(false);
            try {
                T resultado = operacion.ejecutar(conexion);
                conexion.commit();
                return resultado;
            } catch (SQLException | RuntimeException e) {
                try {
                    conexion.rollback();
                } catch (SQLException fallo) {
                    e.addSuppressed(fallo);
                }
                throw e;
            }
        }
    }

    public static <T> List<T> listar(Connection conexion, String consulta, Fila<T> lector, Object... parametros)
            throws SQLException {
        try (PreparedStatement sql = conexion.prepareStatement(consulta)) {
            asignar(sql, parametros);
            try (ResultSet filas = sql.executeQuery()) {
                List<T> resultado = new ArrayList<>();
                while (filas.next()) {
                    resultado.add(lector.leer(filas));
                }
                return resultado;
            }
        }
    }

    public static int actualizar(Connection conexion, String consulta, Object... parametros) throws SQLException {
        try (PreparedStatement sql = conexion.prepareStatement(consulta)) {
            asignar(sql, parametros);
            return sql.executeUpdate();
        }
    }

    public static long insertar(Connection conexion, String consulta, Object... parametros) throws SQLException {
        try (PreparedStatement sql = conexion.prepareStatement(consulta, Statement.RETURN_GENERATED_KEYS)) {
            asignar(sql, parametros);
            sql.executeUpdate();
            try (ResultSet claves = sql.getGeneratedKeys()) {
                if (!claves.next()) {
                    throw new SQLException("La operación no generó un identificador.");
                }
                return claves.getLong(1);
            }
        }
    }

    public static String contiene(String texto) {
        return "%" + texto.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    public static <T> Pagina<T> pagina(Connection conexion, String consulta, Fila<T> lector,
            int pagina, Object... parametros) throws SQLException {
        return Pagina.desde(listar(conexion, consulta + " LIMIT ? OFFSET ?", lector,
                unir(parametros, Pagina.TAMANO + 1, Pagina.desplazamiento(pagina))), pagina);
    }

    public static Object[] unir(Object[] iniciales, Object... finales) {
        Object[] resultado = Arrays.copyOf(iniciales, iniciales.length + finales.length);
        System.arraycopy(finales, 0, resultado, iniciales.length, finales.length);
        return resultado;
    }

    private static void asignar(PreparedStatement sql, Object[] parametros) throws SQLException {
        for (int i = 0; i < parametros.length; i++) {
            sql.setObject(i + 1, parametros[i]);
        }
    }
}
