package pe.gob.sigmud.dao.impl;

import static pe.gob.sigmud.model.ModuloMunicipal.ALMACEN;
import static pe.gob.sigmud.model.ModuloMunicipal.CITAS;
import static pe.gob.sigmud.model.ModuloMunicipal.CONTRIBUYENTES;
import static pe.gob.sigmud.model.ModuloMunicipal.MERCADOS;
import static pe.gob.sigmud.model.ModuloMunicipal.RECLAMOS;
import static pe.gob.sigmud.model.ModuloMunicipal.SEGURIDAD;
import static pe.gob.sigmud.model.ModuloMunicipal.TRAMITES;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import pe.gob.sigmud.dao.AlcanceAreas;
import pe.gob.sigmud.dao.Jdbc;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.Pantalla.Enlace;
import pe.gob.sigmud.model.Pantalla.Fila;
import pe.gob.sigmud.model.Pantalla.Tabla;
import pe.gob.sigmud.model.VistaInicio.Bloque;
import pe.gob.sigmud.model.VistaInicio.Kpi;
import pe.gob.sigmud.model.VistaInicio.Perfil;

public final class PanelPerfilJdbcDao {

    private static final String BANDEJA = """
            FROM tramites t JOIN historial_tramites h ON h.id =
                (SELECT MAX(x.id) FROM historial_tramites x WHERE x.tramite_id = t.id)
            """;
    private static final String ABIERTOS = "r.estado IN ('PENDIENTE', 'EN_ATENCION')";
    private static final String AGENDA = """
            FROM citas c JOIN ventanillas v ON v.id = c.ventanilla_id
            WHERE c.estado = 'PROGRAMADA' AND c.inicio >= CURRENT_TIMESTAMP(6) AND
            """ + AlcanceAreas.condicion("v.area_id");
    private static final String INVENTARIO = """
            SELECT m.id, m.codigo, m.nombre, m.unidad_medida, m.stock_minimo, COALESCE(SUM(l.stock),0) AS stock
            FROM materiales m LEFT JOIN lotes_material l ON m.id = l.material_id
            WHERE m.baja_en IS NULL GROUP BY m.id, m.codigo, m.nombre, m.unidad_medida, m.stock_minimo
            """;
    private static final String OCUPACION = """
            FROM puestos p JOIN mercados m ON m.id = p.mercado_id
            JOIN sectores_mercado s ON s.id = p.sector_id
            LEFT JOIN asignaciones_puestos ap ON ap.puesto_vigente_id = p.id
            WHERE p.baja_en IS NULL AND m.baja_en IS NULL
            """;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public record Contenido(List<Kpi> indicadores, List<Bloque> bloques) {}

    public Contenido consultar(Identidad identidad, Perfil perfil) throws SQLException {
        return Jdbc.leer(conexion -> {
            conexion.setReadOnly(true);
            List<Kpi> indicadores = new ArrayList<>();
            List<Bloque> bloques = new ArrayList<>();
            switch (perfil) {
                case ADMINISTRADOR -> administracion(conexion, identidad, indicadores, bloques);
                case MESA_PARTES -> mesa(conexion, identidad, indicadores, bloques);
                case RENTAS -> rentas(conexion, identidad, indicadores, bloques);
                case FISCALIZACION_DEFENSA_CIVIL -> casos(conexion, identidad, true, indicadores, bloques);
                case ATENCION_VECINO -> {
                    casos(conexion, identidad, false, indicadores, bloques);
                    agenda(conexion, identidad, indicadores, bloques);
                }
                case MERCADOS -> mercados(conexion, identidad, indicadores, bloques);
                case ALMACEN -> almacen(conexion, identidad, indicadores, bloques);
                case REGISTRO_CIVIL -> {
                    personas(conexion, identidad, indicadores, bloques);
                    agenda(conexion, identidad, indicadores, bloques);
                }
                case GENERAL -> { }
            }
            return new Contenido(indicadores, bloques);
        });
    }

    private static void administracion(Connection c, Identidad u, List<Kpi> k, List<Bloque> b) throws SQLException {
        if (!SEGURIDAD.permitido(u)) return;
        long[] cuentas = totales(c, """
                SELECT COUNT(*), SUM(activo = 1), SUM(activo = 0),
                    SUM(activo = 1 AND bloqueado_hasta > CURRENT_TIMESTAMP(6)) FROM usuarios
                """);
        k.add(kpi("Cuentas registradas", cuentas[0], "Total del sistema", "people", "/usuarios"));
        k.add(kpi("Cuentas activas", cuentas[1], "Habilitadas en el padrón", "shield", "/usuarios"));
        k.add(kpi("Cuentas inactivas", cuentas[2], "Con acceso deshabilitado", "user", "/usuarios?estado=inactiva"));
        k.add(alerta("Bloqueos vigentes", cuentas[3], "Por intentos de acceso", "alert", "/usuarios?estado=bloqueada"));
        b.add(bloque("Administración de cuentas", "Últimas cuentas creadas", "Aún no hay cuentas registradas.",
                "shield", "/usuarios", List.of("Cuenta", "Rol", "Estado"),
                Jdbc.listar(c, """
                        SELECT u.id, u.nombre_visible, r.nombre,
                            CASE WHEN u.activo = 0 THEN 'Inactiva'
                                 WHEN u.bloqueado_hasta > CURRENT_TIMESTAMP(6) THEN 'Bloqueada'
                                 ELSE 'Activa' END
                        FROM usuarios u JOIN roles r ON r.id = u.rol_id
                        ORDER BY u.creado_en DESC, u.id DESC LIMIT 5
                        """, f -> Fila.nueva(new Enlace("Editar", "/usuarios?editar=" + f.getLong(1)),
                        f.getString(2), f.getString(3), f.getString(4)))));
    }

    private static void mesa(Connection c, Identidad u, List<Kpi> k, List<Bloque> b) throws SQLException {
        if (!TRAMITES.permitido(u)) return;
        Object[] alcance = AlcanceAreas.parametros(u);
        long[] bandeja = totales(c, "SELECT SUM(h.estado='RECIBIDO'), SUM(h.estado='EN_TRAMITE'), "
                + "SUM(h.estado IN ('RECIBIDO','EN_TRAMITE')) " + BANDEJA + " WHERE "
                + AlcanceAreas.condicion("h.area_id"), alcance);
        long[] propios = totales(c, """
                SELECT (SELECT COUNT(*) FROM tramites WHERE registrado_por = ?),
                       COUNT(DISTINCT h.tramite_id)
                FROM historial_tramites h JOIN historial_tramites anterior ON anterior.id =
                    (SELECT MAX(x.id) FROM historial_tramites x WHERE x.tramite_id = h.tramite_id AND x.id < h.id)
                WHERE h.registrado_por = ? AND h.area_id <> anterior.area_id
                """, u.id(), u.id());
        k.add(kpi("Por gestionar", bandeja[2], "Recibidos y en trámite", "file", "/tramites"));
        k.add(kpi("Recibidos", bandeja[0], "Estado actual en tu área", "file", "/tramites?estado=RECIBIDO"));
        k.add(kpi("En trámite", bandeja[1], "Expedientes en curso", "clock", "/tramites?estado=EN_TRAMITE"));
        k.add(kpi("Derivados por ti", propios[1], "Expedientes con cambio de área", "arrow", null));
        b.add(bandeja(c, u, "Bandeja documentaria", "Movimientos más recientes en tu área"));
        b.add(bloque("Registro y derivación", "Resumen de tus operaciones documentarias",
                "Aún no has registrado expedientes.", "chart", null, List.of("Operación", "Expedientes"),
                List.of(Fila.texto("Registrados por ti", Long.toString(propios[0])),
                        Fila.texto("Derivados por ti", Long.toString(propios[1])))));
    }

    private static void rentas(Connection c, Identidad u, List<Kpi> k, List<Bloque> b) throws SQLException {
        if (CONTRIBUYENTES.permitido(u)) {
            long[] padrón = totales(c, """
                    SELECT SUM(tipo_persona='NATURAL'), SUM(tipo_persona='JURIDICA')
                    FROM contribuyentes WHERE baja_en IS NULL
                    """);
            long[] predios = totales(c, """
                    SELECT COUNT(DISTINCT p.codigo_predio) FROM contribuyentes_predios p
                    JOIN contribuyentes t ON t.id=p.contribuyente_id WHERE t.baja_en IS NULL
                    """);
            k.add(kpi("Personas naturales", padrón[0], "Vigentes en el padrón", "people", "/contribuyentes?tipo=NATURAL"));
            k.add(kpi("Personas jurídicas", padrón[1], "Vigentes en el padrón", "file", "/contribuyentes?tipo=JURIDICA"));
            k.add(kpi("Predios vinculados", predios[0], "Códigos asociados a registros vigentes", "home", "/busquedas?tipo=PREDIOS"));
            b.add(bloque("Vínculos prediales", "Registros vigentes por código de predio",
                    "Aún no hay predios vinculados a contribuyentes vigentes.", "home", "/busquedas?tipo=PREDIOS",
                    List.of("Predio", "Contribuyente", "Tipo"),
                    Jdbc.listar(c, """
                            SELECT t.id, p.codigo_predio, t.nombre_razon_social, t.tipo_persona
                            FROM contribuyentes_predios p JOIN contribuyentes t ON t.id = p.contribuyente_id
                            WHERE t.baja_en IS NULL ORDER BY p.codigo_predio,t.id LIMIT 5
                            """, f -> ficha(f.getLong(1), f.getString(2), f.getString(3), etiqueta(f.getString(4))))));
        }
        if (TRAMITES.permitido(u)) {
            k.add(kpi("Expedientes en curso", expedientes(c, u, "h.estado IN ('RECIBIDO','EN_TRAMITE')"),
                    "En el ámbito de Rentas", "file", "/tramites"));
            b.add(bandeja(c, u, "Expedientes de Rentas", "Trámites que corresponden a tu área"));
        }
    }

    private static void personas(Connection c, Identidad u, List<Kpi> k, List<Bloque> b) throws SQLException {
        if (CONTRIBUYENTES.permitido(u)) {
            long[] personas = totales(c, """
                    SELECT COUNT(*), COUNT(dni) FROM contribuyentes
                    WHERE tipo_persona = 'NATURAL' AND baja_en IS NULL
                    """);
            k.add(kpi("Personas naturales", personas[0], "Fichas personales vigentes", "people", "/contribuyentes?tipo=NATURAL"));
            k.add(kpi("Con DNI registrado", personas[1], "Dentro del padrón de personas", "shield", "/busquedas?tipo=DOCUMENTOS"));
            b.add(bloque("Fichas de personas", "Personas naturales vigentes, ordenadas por nombre",
                    "Aún no hay fichas vigentes de personas naturales.", "people", "/contribuyentes?tipo=NATURAL",
                    List.of("Persona", "Documento"),
                    Jdbc.listar(c, """
                            SELECT id, nombre_razon_social, RIGHT(dni, 4) AS dni_parcial,
                                   RIGHT(ruc, 4) AS ruc_parcial
                            FROM contribuyentes WHERE tipo_persona = 'NATURAL' AND baja_en IS NULL
                            ORDER BY nombre_razon_social,id LIMIT 5
                            """, f -> ficha(f.getLong(1), f.getString(2), documentoParcial(f)))));
        }
        if (TRAMITES.permitido(u)) {
            k.add(kpi("Expedientes atendidos", expedientes(c, u, "h.estado = 'ATENDIDO'"),
                    "Estado actual en Registro Civil", "file", "/tramites?estado=ATENDIDO"));
        }
    }

    private static void casos(Connection c, Identidad u, boolean defensa, List<Kpi> k, List<Bloque> b) throws SQLException {
        if (!RECLAMOS.permitido(u)) return;
        Object[] alcance = AlcanceAreas.parametros(u);
        long[] totales = totales(c, """
                SELECT SUM(r.estado = 'PENDIENTE'), SUM(r.estado = 'EN_ATENCION'), SUM(r.estado = 'ATENDIDO'),
                    SUM(r.tipo = 'EMERGENCIA' AND r.estado IN ('PENDIENTE','EN_ATENCION')),
                    SUM(r.prioridad IN ('ALTA','CRITICA') AND r.estado IN ('PENDIENTE','EN_ATENCION'))
                FROM reclamos_emergencias r WHERE
                """ + AlcanceAreas.condicion("r.area_id"), alcance);
        if (defensa) {
            k.add(alerta("Prioridad alta o crítica", totales[4], "Casos abiertos en las áreas autorizadas", "alert", "/reclamos?estado=ABIERTOS&prioridad=URGENTE"));
            k.add(kpi("Emergencias abiertas", totales[3], "Pendientes o en atención", "alert", "/reclamos?tipo=EMERGENCIA&estado=ABIERTOS"));
        }
        k.add(kpi("Por atender", totales[0], "Casos pendientes en tu ámbito", "clock", "/reclamos?estado=PENDIENTE"));
        k.add(kpi("En atención", totales[1], "Casos con atención iniciada", "people", "/reclamos?estado=EN_ATENCION"));
        if (!defensa) k.add(kpi("Atendidos", totales[2], "Casos con atención registrada", "shield", "/reclamos?estado=ATENDIDO"));

        String consulta = """
                SELECT r.id, r.codigo_caso, r.tipo, r.prioridad, r.estado, a.nombre
                FROM reclamos_emergencias r JOIN areas a ON a.id = r.area_id WHERE
                """ + AlcanceAreas.condicion("r.area_id")
                + (defensa ? " AND " + ABIERTOS + " ORDER BY r.prioridad DESC,r.registrado_en,r.id"
                           : " ORDER BY COALESCE(r.atendido_en,r.registrado_en) DESC,r.id DESC")
                + " LIMIT 5";
        b.add(bloque(defensa ? "Cola de respuesta" : "Seguimiento de solicitudes",
                defensa ? "Casos abiertos por prioridad y antigüedad" : "Registros del área por fecha de atención o alta",
                defensa ? "No hay casos abiertos en las áreas autorizadas." : "Aún no hay solicitudes en tu área.",
                defensa ? "alert" : "people", defensa ? "/reclamos" : "/reclamos?estado=",
                defensa ? List.of("Caso", "Prioridad", "Estado", "Área") : List.of("Caso", "Tipo", "Estado", "Prioridad"),
                Jdbc.listar(c, consulta, f -> Fila.nueva(new Enlace("Abrir", "/reclamos?id=" + f.getLong(1)),
                        f.getString(2), defensa ? etiqueta(f.getString(4)) : etiqueta(f.getString(3)),
                        etiqueta(f.getString(5)), defensa ? f.getString(6) : etiqueta(f.getString(4))), alcance)));
        if (defensa) {
            b.add(bloque("Prioridades abiertas", "Distribución de la cola de respuesta",
                    "No hay prioridades abiertas en tu ámbito.", "chart", "/reclamos", List.of("Prioridad", "Casos"),
                    Jdbc.listar(c, "SELECT r.prioridad,COUNT(*) FROM reclamos_emergencias r WHERE " + ABIERTOS
                            + " AND " + AlcanceAreas.condicion("r.area_id") + " GROUP BY r.prioridad ORDER BY r.prioridad DESC",
                            f -> Fila.nueva(new Enlace("Ver cola", "/reclamos?estado=ABIERTOS&prioridad=" + f.getString(1)),
                                    etiqueta(f.getString(1)), f.getString(2)), alcance)));
        }
    }

    private static void agenda(Connection c, Identidad u, List<Kpi> k, List<Bloque> b) throws SQLException {
        if (!CITAS.permitido(u)) return;
        Object[] alcance = AlcanceAreas.parametros(u);
        long citas = totales(c, "SELECT COUNT(*) " + AGENDA, alcance)[0];
        k.add(kpi("Citas próximas", citas, "Reservas futuras de tu área", "calendar", "/citas?estado=PROGRAMADA"));
        b.add(bloque("Próximas citas", "Primeros horarios en la agenda del área",
                "No hay citas programadas en tu ámbito.", "calendar", "/citas?estado=PROGRAMADA",
                List.of("Cita", "Ventanilla", "Inicio"), Jdbc.listar(c,
                        "SELECT c.id,c.codigo_cita,v.nombre,c.inicio " + AGENDA + " ORDER BY c.inicio,c.id LIMIT 4",
                        f -> Fila.nueva(new Enlace("Abrir cita", "/citas?id=" + f.getLong(1)),
                                f.getString(2), f.getString(3), fecha(f,4)), alcance)));
    }

    private static void mercados(Connection c, Identidad u, List<Kpi> k, List<Bloque> b) throws SQLException {
        if (!MERCADOS.permitido(u)) return;
        long[] puestos = totales(c, "SELECT COUNT(*), COUNT(ap.id), COUNT(DISTINCT p.sector_id) " + OCUPACION);
        long mercados = totales(c, "SELECT COUNT(*) FROM mercados WHERE baja_en IS NULL")[0];
        k.add(kpi("Mercados vigentes", mercados, "En el catálogo de mercados", "store", "/mercados?catalogo=MERCADOS"));
        k.add(kpi("Sectores con puestos", puestos[2], "Con puestos vigentes", "home", "/mercados?catalogo=SECTORES"));
        k.add(kpi("Puestos ocupados", puestos[1], "Con asignación vigente", "user", "/mercados?estado=OCUPADO"));
        k.add(kpi("Puestos libres", puestos[0] - puestos[1], "Disponibles para asignar", "store", "/mercados?estado=LIBRE"));
        b.add(bloque("Disponibilidad por sector", "Ocupación de puestos vigentes por mercado",
                "No hay puestos vigentes en los mercados del catálogo.", "store", "/mercados",
                List.of("Mercado", "Sector", "Ocupados", "Libres"),
                Jdbc.listar(c, "SELECT m.id,s.id,m.nombre,s.nombre,COUNT(ap.id),COUNT(*)-COUNT(ap.id) "
                        + OCUPACION + " GROUP BY m.id,s.id,m.nombre,s.nombre ORDER BY m.nombre,s.nombre LIMIT 6",
                        f -> Fila.nueva(new Enlace("Consultar", "/mercados?mercado=" + f.getLong(1) + "&sector=" + f.getLong(2)),
                                f.getString(3), f.getString(4), f.getString(5), f.getString(6)))));
        b.add(bloque("Movimientos de puestos", "Últimas asignaciones y liberaciones registradas",
                "Aún no se registraron movimientos de puestos.", "file", "/mercados",
                List.of("Puesto", "Mercado", "Movimiento", "Fecha"),
                Jdbc.listar(c, """
                        SELECT p.id, p.numero, m.nombre, ap.liberado_en IS NOT NULL AS liberado,
                               COALESCE(ap.liberado_en, ap.asignado_en) AS fecha
                        FROM asignaciones_puestos ap
                        JOIN puestos p ON p.id = ap.puesto_id JOIN mercados m ON m.id = p.mercado_id
                        ORDER BY fecha DESC, ap.id DESC LIMIT 5
                        """, f -> Fila.nueva(new Enlace("Abrir puesto", "/mercados?id=" + f.getLong(1)),
                                f.getString(2), f.getString(3), f.getBoolean(4) ? "Liberación" : "Asignación",
                                fecha(f,5)))));
    }

    private static void almacen(Connection c, Identidad u, List<Kpi> k, List<Bloque> b) throws SQLException {
        if (!ALMACEN.permitido(u)) return;
        long[] stock = totales(c, "SELECT COUNT(*),SUM(stock <= stock_minimo) FROM (" + INVENTARIO + ") inventario");
        long lotes = totales(c, """
                SELECT COUNT(*) FROM lotes_material l JOIN materiales m ON m.id = l.material_id
                WHERE m.baja_en IS NULL AND l.stock > 0 AND l.vence_en <= DATE_ADD(CURRENT_DATE, INTERVAL 30 DAY)
                """)[0];
        long[] movimientos = totales(c, """
                SELECT SUM(tipo='INGRESO'),SUM(tipo='SALIDA') FROM movimientos_stock
                WHERE registrado_en BETWEEN DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 30 DAY) AND CURRENT_TIMESTAMP(6)
                """);
        k.add(kpi("Materiales vigentes", stock[0], "Referencias del inventario", "box", "/almacen"));
        k.add(alerta("Stock en el mínimo o menor", stock[1], "Materiales que requieren revisión", "alert", "/almacen?alerta=BAJO"));
        k.add(alerta("Lotes a revisar", lotes, "Vencidos o por vencer en 30 días con saldo", "clock", "/almacen?alerta=VENCIMIENTOS"));
        k.add(kpi("Movimientos en 30 días", movimientos[0] + movimientos[1],
                movimientos[0] + " ingresos · " + movimientos[1] + " salidas", "chart", null));
        b.add(bloque("Materiales con stock bajo", "Existencias en el mínimo configurado o por debajo",
                "No hay materiales en el mínimo ni por debajo.", "box", "/almacen?alerta=BAJO",
                List.of("Material", "Existencia", "Mínimo"), Jdbc.listar(c,
                        "SELECT id,codigo,nombre,stock,stock_minimo,unidad_medida FROM (" + INVENTARIO + ") inventario"
                        + " WHERE stock <= stock_minimo ORDER BY stock-stock_minimo,nombre,id LIMIT 5",
                        f -> Fila.nueva(new Enlace("Ver material", "/almacen?id=" + f.getLong(1)),
                                f.getString(2) + " · " + f.getString(3), f.getBigDecimal(4).stripTrailingZeros().toPlainString()
                                        + " " + f.getString(6), f.getBigDecimal(5).stripTrailingZeros().toPlainString()))));
        b.add(bloque("Últimos movimientos", "Ingresos y salidas registrados hasta el momento",
                "Aún no hay movimientos con fecha transcurrida.", "chart", "/almacen",
                List.of("Material", "Movimiento", "Cantidad"), Jdbc.listar(c, """
                        SELECT m.id,m.nombre,x.tipo,x.cantidad FROM movimientos_stock x
                        JOIN lotes_material l ON l.id=x.lote_id JOIN materiales m ON m.id=l.material_id
                        WHERE m.baja_en IS NULL AND x.registrado_en <= CURRENT_TIMESTAMP(6)
                        ORDER BY x.registrado_en DESC,x.id DESC LIMIT 5
                        """, f -> Fila.nueva(new Enlace("Ver historial", "/almacen?id=" + f.getLong(1)),
                        f.getString(2), etiqueta(f.getString(3)), f.getBigDecimal(4).stripTrailingZeros().toPlainString()))));
    }

    private static long expedientes(Connection c, Identidad u, String condicion) throws SQLException {
        return totales(c, "SELECT COUNT(*) " + BANDEJA + " WHERE " + condicion + " AND "
                + AlcanceAreas.condicion("h.area_id"), AlcanceAreas.parametros(u))[0];
    }

    private static Bloque bandeja(Connection c, Identidad u, String titulo, String detalle) throws SQLException {
        return bloque(titulo, detalle, "Aún no hay expedientes en el área asignada.", "file", "/tramites",
                List.of("Expediente", "Tipo de trámite", "Estado"), Jdbc.listar(c,
                        "SELECT t.id,t.numero_expediente,t.tipo_tramite,h.estado " + BANDEJA + " WHERE "
                        + AlcanceAreas.condicion("h.area_id") + " ORDER BY h.registrado_en DESC,h.id DESC LIMIT 5",
                        f -> Fila.nueva(new Enlace("Seguimiento", "/tramites?id=" + f.getLong(1)), f.getString(2),
                                f.getString(3), etiqueta(f.getString(4))), AlcanceAreas.parametros(u)));
    }

    private static String documentoParcial(ResultSet fila) throws SQLException {
        String dni = fila.getString("dni_parcial");
        String ruc = fila.getString("ruc_parcial");
        if (dni != null) return "DNI · …" + dni;
        return ruc == null ? "—" : "RUC · …" + ruc;
    }

    private static Fila ficha(long id, String... celdas) {
        return Fila.nueva(new Enlace("Abrir ficha", "/contribuyentes?id=" + id), celdas);
    }

    private static long[] totales(Connection c, String sql, Object... parametros) throws SQLException {
        return Jdbc.listar(c, sql, f -> {
            long[] numeros = new long[f.getMetaData().getColumnCount()];
            for (int i = 0; i < numeros.length; i++) numeros[i] = f.getLong(i + 1);
            return numeros;
        }, parametros).get(0);
    }

    private static String fecha(ResultSet f, int columna) throws SQLException {
        return f.getObject(columna, LocalDateTime.class).format(FECHA);
    }

    private static String etiqueta(String codigo) {
        return switch (codigo) {
            case "CRITICA" -> "Crítica";
            case "EN_ATENCION" -> "En atención";
            case "EN_TRAMITE" -> "En trámite";
            case "JURIDICA" -> "Jurídica";
            default -> codigo.substring(0, 1) + codigo.substring(1).toLowerCase(java.util.Locale.ROOT);
        };
    }

    private static Kpi kpi(String titulo, long valor, String descripcion, String icono, String destino) {
        return new Kpi(titulo, valor, descripcion, icono, destino, false);
    }

    private static Kpi alerta(String titulo, long valor, String descripcion, String icono, String destino) {
        return new Kpi(titulo, valor, descripcion, icono, destino, valor > 0);
    }

    private static Bloque bloque(String titulo, String descripcion, String vacio, String icono, String destino,
            List<String> columnas, List<Fila> filas) {
        return new Bloque(new Tabla(titulo, columnas, filas, filas.stream().anyMatch(fila -> fila.enlace() != null)),
                descripcion, vacio, icono, destino);
    }
}
