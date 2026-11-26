package pe.gob.sigmud.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import pe.gob.sigmud.dao.impl.BusquedaJdbcDao.Orden;
import pe.gob.sigmud.dao.impl.BusquedaJdbcDao.Tipo;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.ModuloMunicipal;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla;
import pe.gob.sigmud.model.Pantalla.*;
import pe.gob.sigmud.service.BusquedasService;
import pe.gob.sigmud.util.Validacion;

@WebServlet("/busquedas")
public final class BusquedasServlet extends ModuloServlet {
    private static final String BASE = "/busquedas";
    private final BusquedasService busquedas = new BusquedasService();

    private record Columnas(String criterio, String codigo, String titulo, String referencia) {}
    @Override protected ModuloMunicipal modulo() { return ModuloMunicipal.BUSQUEDAS; }

    @Override
    protected Pantalla vista(HttpServletRequest r, Identidad u) throws SQLException {
        String q = dato(r, "q"), orden = dato(r, "orden");
        var tipos = busquedas.tipos(u).stream().sorted(Comparator.comparing(Tipo::etiqueta)).toList();
        Tipo preferido = switch (u.codigoRol()) {
            case "MESA_PARTES" -> Tipo.EXPEDIENTES;
            case "RENTAS" -> Tipo.PREDIOS;
            case "REGISTRO_CIVIL", "ATENCION_VECINO" -> Tipo.DOCUMENTOS;
            case "FISCALIZACION_DEFENSA_CIVIL" -> Tipo.RECLAMOS;
            case "MERCADOS" -> Tipo.PUESTOS;
            case "ALMACEN" -> Tipo.MATERIALES;
            default -> null;
        };
        String tipo = r.getParameter("tipo") == null && q.isEmpty() && preferido != null && tipos.contains(preferido)
                ? preferido.name() : dato(r, "tipo");
        Tipo seleccionado = tipo.isEmpty() ? null : Validacion.opcion(Tipo.class, tipo, "tipo");
        if (seleccionado != null && !tipos.contains(seleccionado)) throw new SecurityException("Búsqueda no autorizada.");
        Pantalla p = new Pantalla("Búsquedas y clasificación",
                "Busca por tipo de registro y abre la ficha desde los resultados.", BASE);
        p.vista("Todos los tipos", BASE + "?tipo=", seleccionado == null);
        for (Tipo disponible : tipos) {
            p.vista(disponible.etiqueta(), BASE + "?tipo=" + disponible.name(), seleccionado == disponible);
        }
        boolean admiteArea = seleccionado == null ? tipos.stream().anyMatch(Tipo::porArea) : seleccionado.porArea();
        String area = admiteArea ? dato(r, "area") : "";
        var areas = admiteArea ? busquedas.areas(u) : List.<Opcion>of();
        Columnas columnas = columnas(seleccionado);
        String etiquetaArea = seleccionado == null ? "Área (solo expedientes, casos y citas)" : columnas.referencia();
        p.filtros(new Formulario(seleccionado == null ? "" : "Búsqueda por " + seleccionado.etiqueta(),
                "Buscar", "", "", List.of(
                Campo.texto("q", columnas.criterio(), q, true, 180),
                Campo.oculto("tipo", tipo),
                areas.size() > 1 ? Campo.seleccionar("area", etiquetaArea, area, false, areas) : Campo.oculto("area", area),
                seleccionado == null ? Campo.seleccionar("orden", "Orden", orden.isEmpty() ? "TIPO" : orden, true,
                        List.of(new Opcion("TIPO", "Tipo y título"), new Opcion("TITULO", "Título")))
                        : Campo.oculto("orden", orden))));
        if (q.isBlank()) {
            p.estadoVacio("Escribe un criterio de búsqueda para consultar los registros autorizados.");
            return p;
        }
        p.estadoVacio("No se encontraron resultados con estos criterios.");
        var resultado = busquedas.buscar(u, seleccionado, q,
                Validacion.idOpcional(area, "área"), orden.isEmpty() ? Orden.TIPO : Validacion.opcion(Orden.class, orden, "orden"),
                Pagina.numero(r.getParameter("pagina")));
        List<String> encabezados = seleccionado == null ? List.of("Tipo", "Código", "Título", "Referencia")
                : List.of(columnas.codigo(), columnas.titulo(), columnas.referencia());
        var filas = resultado.filas().stream().map(f -> {
            Enlace ficha = new Enlace("Abrir ficha", f.tipo().modulo().ruta() + "?id=" + f.id());
            return seleccionado == null ? Fila.nueva(ficha, f.tipo().etiqueta(), f.codigo(), f.titulo(), f.referencia())
                    : Fila.nueva(ficha, f.codigo(), f.titulo(), f.referencia());
        }).toList();
        p.tablas().add(tabla("Resultados", encabezados, filas, true, resultado, BASE,
                Map.of("q", q, "tipo", tipo, "area", area, "orden", orden)));
        return p;
    }

    private static Columnas columnas(Tipo tipo) {
        if (tipo == null) return new Columnas("Nombre, documento o código", "Código", "Título", "Referencia");
        return switch (tipo) {
            case CONTRIBUYENTES -> new Columnas("Nombre o razón social", "Documento", "Nombre o razón social", "Tipo de persona");
            case DOCUMENTOS -> new Columnas("DNI o RUC", "DNI o RUC", "Nombre o razón social", "Referencia");
            case PREDIOS -> new Columnas("Código de predio", "Código de predio", "Titular vinculado", "Referencia");
            case EXPEDIENTES -> new Columnas("Número de expediente o tipo de trámite", "Expediente", "Tipo de trámite", "Área actual");
            case RECLAMOS -> new Columnas("Código del caso", "Caso", "Tipo de caso", "Área responsable");
            case CITAS -> new Columnas("Código de cita o nombre de ventanilla", "Cita", "Ventanilla", "Área de atención");
            case PUESTOS -> new Columnas("Número de puesto, mercado o sector", "Puesto", "Mercado", "Sector");
            case MATERIALES -> new Columnas("Código o nombre del material", "Código de material", "Material", "Unidad");
        };
    }

    @Override
    protected Cambio operar(HttpServletRequest r, Identidad u) {
        throw new IllegalArgumentException("Escribe un criterio de búsqueda.");
    }
}
