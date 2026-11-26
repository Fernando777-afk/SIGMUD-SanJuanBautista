package pe.gob.sigmud.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.Pantalla.Opcion;

public final class AlcanceAreas {
    private AlcanceAreas() {}

    public static String condicion(String columna) {
        return """
                (? OR %s IN (
                    SELECT a.id FROM areas a WHERE a.baja_en IS NULL
                      AND CASE WHEN ? THEN a.codigo IN ('FISCALIZACION', 'DEFENSA_CIVIL')
                               ELSE a.id = (SELECT u.area_id FROM usuarios u WHERE u.id = ?)
                          END
                ))
                """.formatted(columna);
    }

    public static Object[] parametros(Identidad identidad) {
        return new Object[] {
            "ADMINISTRADOR".equals(identidad.codigoRol()),
            "FISCALIZACION_DEFENSA_CIVIL".equals(identidad.codigoRol()),
            identidad.id()
        };
    }

    public static List<Opcion> areas(Connection conexion, Identidad identidad, boolean soloAutorizadas) throws SQLException {
        String sql = "SELECT id, nombre FROM areas WHERE baja_en IS NULL"
                + (soloAutorizadas ? " AND " + condicion("areas.id") : "") + " ORDER BY nombre";
        return Jdbc.listar(conexion, sql,
                fila -> new Opcion(fila.getLong("id"), fila.getString("nombre")),
                soloAutorizadas ? parametros(identidad) : new Object[0]);
    }

    public static boolean permite(Connection conexion, Identidad identidad, long area) throws SQLException {
        return !Jdbc.listar(conexion, "SELECT id FROM areas WHERE id = ? AND " + condicion("areas.id"),
                fila -> fila.getLong(1), Jdbc.unir(new Object[] {area}, parametros(identidad))).isEmpty();
    }
}
