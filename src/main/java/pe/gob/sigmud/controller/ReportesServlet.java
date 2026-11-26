package pe.gob.sigmud.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import pe.gob.sigmud.dao.impl.ReportesJdbcDao.Filtros;
import pe.gob.sigmud.dao.impl.ReportesJdbcDao.Reporte;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.ModuloMunicipal;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla;
import pe.gob.sigmud.model.Pantalla.*;
import pe.gob.sigmud.service.ReportesService;
import pe.gob.sigmud.util.Validacion;

@WebServlet("/reportes")
public final class ReportesServlet extends ModuloServlet {
    private static final String BASE = "/reportes";
    private final ReportesService reportes = new ReportesService();
    @Override protected ModuloMunicipal modulo() { return ModuloMunicipal.REPORTES; }

    @Override
    protected Pantalla vista(HttpServletRequest r, Identidad u) throws SQLException {
        List<Reporte> opciones = reportes.autorizados(u);
        Pantalla p = new Pantalla("Reportes de gestión", "Elige un reporte de tu ámbito y filtra los resultados.", BASE);
        if (opciones.isEmpty()) {
            p.estadoVacio("Tu perfil no tiene reportes autorizados para los módulos de origen.");
            return p;
        }
        Reporte seleccionado = seleccionar(r, u, opciones);
        for (Reporte opcion : opciones) {
            p.vista(opcion.titulo(), BASE + "?reporte=" + opcion.name(), opcion == seleccionado);
        }
        p.estadoVacio("No se encontraron datos para este reporte con los filtros indicados.");
        var datos = reportes.listar(u, seleccionado, filtros(r, seleccionado), Pagina.numero(r.getParameter("pagina")));
        var campos = campos(r, u, seleccionado);
        if (campos.size() > 1) p.filtros(new Formulario("Filtros del reporte", "Consultar", "", "", campos));
        p.enlaces().add(new Enlace("Exportar CSV", ruta(BASE, parametros(r, seleccionado, true))));
        r.setAttribute("imprimirDisponible", true);
        p.tablas().add(tabla(seleccionado.titulo(), seleccionado.columnas(),
                datos.filas().stream().map(f -> Fila.texto(f.toArray(String[]::new))).toList(),
                false, datos, BASE, parametros(r, seleccionado, false)));
        r.setAttribute("notaModulo", seleccionado.descripcion());
        return p;
    }

    private List<Campo> campos(HttpServletRequest r, Identidad u, Reporte seleccionado) throws SQLException {
        List<Campo> campos = new ArrayList<>();
        campos.add(Campo.oculto("reporte", seleccionado.name()));
        if (seleccionado.fechas()) {
            campos.add(Campo.tipo("desde", "Desde", "date", dato(r, "desde"), false));
            campos.add(Campo.tipo("hasta", "Hasta", "date", dato(r, "hasta"), false));
        }
        if (seleccionado.areas()) {
            var areas = reportes.areas(u, seleccionado);
            String area = dato(r, "area");
            if (areas.size() > 1) {
                String etiqueta = switch (seleccionado) {
                    case MOVIMIENTOS -> "Área solicitante";
                    case CITAS -> "Área de atención";
                    case RECLAMOS -> "Área responsable";
                    case TRAMITES_ESTADO -> "Área actual";
                    case TRAMITES_AREA -> "Área que atendió";
                    default -> "Área municipal";
                };
                campos.add(Campo.seleccionar("area", etiqueta, area, false, areas));
            } else if (!area.isBlank()) {
                campos.add(Campo.oculto("area", area));
            }
        }
        if (!seleccionado.estados().isEmpty()) {
            String etiqueta = switch (seleccionado) {
                case MOVIMIENTOS -> "Tipo de movimiento";
                case CITAS -> "Estado de la cita";
                case RECLAMOS -> "Estado de atención";
                default -> "Estado del expediente";
            };
            campos.add(Campo.seleccionar("estado", etiqueta, dato(r, "estado"), false, opciones(seleccionado.estados())));
        }
        return campos;
    }

    @Override
    protected boolean descargar(HttpServletRequest r, HttpServletResponse respuesta, Identidad u)
            throws IOException, SQLException {
        if (r.getParameter("csv") == null) return false;
        Reporte seleccionado = seleccionar(r, u, reportes.autorizados(u));
        var datos = reportes.exportar(u, seleccionado, filtros(r, seleccionado));
        respuesta.setContentType("text/csv;charset=UTF-8");
        respuesta.setHeader("Content-Disposition", "attachment; filename=\"sigmud-" + seleccionado.name().toLowerCase(java.util.Locale.ROOT) + ".csv\"");
        var salida = respuesta.getWriter();
        salida.write('\uFEFF');
        salida.write(filaCsv(seleccionado.columnas()));
        for (List<String> fila : datos) salida.write(filaCsv(fila));
        return true;
    }

    static String filaCsv(List<String> columnas) {
        return columnas.stream().map(ReportesServlet::celdaCsv).collect(Collectors.joining(",")) + "\r\n";
    }

    private static String celdaCsv(String valor) {
        String dato = valor == null ? "" : valor;
        String limpio = dato.stripLeading();
        if ((!limpio.isEmpty() && "=+-@".indexOf(limpio.charAt(0)) >= 0)
                || dato.startsWith("\t") || dato.startsWith("\r") || dato.startsWith("\n")) {
            dato = "'" + dato;
        }
        return "\"" + dato.replace("\"", "\"\"") + "\"";
    }

    private static Reporte seleccionar(HttpServletRequest r, Identidad u, List<Reporte> autorizados) {
        if (autorizados.isEmpty()) throw new SecurityException("Reporte no autorizado.");
        String nombre = dato(r, "reporte");
        Reporte preferido = switch (u.codigoRol()) {
            case "MESA_PARTES" -> Reporte.TRAMITES_ESTADO;
            case "ATENCION_VECINO", "FISCALIZACION_DEFENSA_CIVIL" -> Reporte.RECLAMOS;
            case "REGISTRO_CIVIL" -> Reporte.CITAS;
            case "MERCADOS" -> Reporte.OCUPACION;
            case "ALMACEN" -> Reporte.STOCK;
            default -> Reporte.CONTRIBUYENTES;
        };
        Reporte elegido = nombre.isBlank() ? (autorizados.contains(preferido) ? preferido : autorizados.get(0))
                : Validacion.opcion(Reporte.class, nombre, "reporte");
        if (!autorizados.contains(elegido)) throw new SecurityException("Reporte no autorizado.");
        return elegido;
    }

    private Filtros filtros(HttpServletRequest r, Reporte elegido) {
        String estado = dato(r, "estado");
        if (!elegido.estados().contains(estado)) estado = "";
        return reportes.filtros(elegido, dato(r, "desde"), dato(r, "hasta"), dato(r, "area"), estado);
    }

    private static Map<String, Object> parametros(HttpServletRequest r, Reporte seleccionado, boolean csv) {
        Map<String, Object> filtros = new java.util.LinkedHashMap<>();
        filtros.put("reporte", seleccionado.name());
        if (seleccionado.fechas()) {
            filtros.put("desde", dato(r, "desde"));
            filtros.put("hasta", dato(r, "hasta"));
        }
        if (seleccionado.areas()) filtros.put("area", dato(r, "area"));
        if (seleccionado.estados().contains(dato(r, "estado"))) filtros.put("estado", dato(r, "estado"));
        if (csv) filtros.put("csv", 1);
        return filtros;
    }

    @Override
    protected Cambio operar(HttpServletRequest r, Identidad u) {
        throw new IllegalArgumentException("Selecciona un reporte para consultar o exportar.");
    }
}
