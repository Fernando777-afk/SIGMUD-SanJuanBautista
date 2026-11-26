package pe.gob.sigmud.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import pe.gob.sigmud.dao.impl.MercadoJdbcDao.Catalogo;
import pe.gob.sigmud.dao.impl.MercadoJdbcDao.Puesto;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.ModuloMunicipal;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla;
import pe.gob.sigmud.model.Pantalla.*;
import pe.gob.sigmud.service.MercadosService;
import pe.gob.sigmud.util.Validacion;

@WebServlet("/mercados")
public final class MercadosServlet extends ModuloServlet {
    private static final String BASE = "/mercados";
    private static final String GESTIONAR = "MERCADOS_GESTIONAR";
    private final MercadosService mercados = new MercadosService();
    @Override protected ModuloMunicipal modulo() { return ModuloMunicipal.MERCADOS; }

    @Override
    protected Pantalla vista(HttpServletRequest r, Identidad u) throws SQLException {
        Long id = Validacion.idOpcional(dato(r, "id"), "puesto");
        if (id != null) return detalle(r, u, mercados.detalle(u, id));
        if (r.getParameter("catalogo") != null) return catalogo(r, u);
        if (r.getParameter("nuevo") != null) return alta(u);
        String q = dato(r, "q"), mercado = dato(r, "mercado"), sector = dato(r, "sector"), estado = dato(r, "estado");
        var registros = mercados.puestos(u, q, Validacion.idOpcional(mercado, "mercado"),
                Validacion.idOpcional(sector, "sector"), estado, Pagina.numero(r.getParameter("pagina")));
        Pantalla p = new Pantalla("Mercados y puestos", "Consulta disponibilidad, ocupación y asignaciones.", BASE);
        if (u.permisos().contains(GESTIONAR)) p.enlaces().add(new Enlace("Registrar puesto", BASE + "?nuevo=1"));
        navegacion(p, null, estado);
        p.estadoVacio("No hay puestos que coincidan con estos filtros.");
        p.filtros(new Formulario("", "Filtrar", "", "", List.of(Campo.texto("q", "Número de puesto", q, false, 30),
                Campo.seleccionar("mercado", "Mercado", mercado, false, catalogo(u, Catalogo.MERCADOS)),
                Campo.seleccionar("sector", "Sector", sector, false, catalogo(u, Catalogo.SECTORES)),
                Campo.seleccionar("estado", "Disponibilidad", estado, false, opciones(List.of("LIBRE", "OCUPADO"))))));
        p.tablas().add(new Tabla("Ocupación general por mercado", List.of("Mercado", "Puestos", "Ocupados", "Libres"),
                mercados.ocupacion(u).stream().map(o -> Fila.texto(o.mercado(), Long.toString(o.puestos()),
                        Long.toString(o.ocupados()), Long.toString(o.puestos() - o.ocupados()))).toList(), false));
        p.tablas().add(tabla("Puestos", List.of("Número de puesto", "Mercado", "Sector", "Disponibilidad"),
                registros.filas().stream().map(puesto -> Fila.nueva(new Enlace("Ver puesto", BASE + "?id=" + puesto.id()),
                        puesto.numero(), puesto.mercado(), puesto.sector(), puesto.ocupado() ? "Ocupado" : "Libre")).toList(),
                true, registros, BASE, Map.of("q", q, "mercado", mercado, "sector", sector, "estado", estado)));
        return p;
    }

    private Pantalla alta(Identidad u) throws SQLException {
        Validacion.permiso(u, GESTIONAR);
        Pantalla p = new Pantalla("Registrar puesto", "Selecciona el mercado y el sector del puesto.", BASE);
        p.enlaces().add(new Enlace("Cancelar", BASE));
        p.formularios().add(new Formulario("Datos del puesto", "Registrar puesto", "alta", "", List.of(
                Campo.seleccionar("mercado", "Mercado", "", true, catalogo(u, Catalogo.MERCADOS)),
                Campo.seleccionar("sector", "Sector", "", true, catalogo(u, Catalogo.SECTORES)),
                Campo.texto("numero", "Número", "", true, 30))));
        return p;
    }

    private Pantalla catalogo(HttpServletRequest r, Identidad u) throws SQLException {
        Catalogo c = Validacion.opcion(Catalogo.class, dato(r, "catalogo"), "catálogo");
        Long editar = Validacion.idOpcional(dato(r, "editar"), "registro");
        String titulo = c == Catalogo.MERCADOS ? "Mercados" : "Sectores del mercado";
        String rutaCatalogo = BASE + "?catalogo=" + c.name();
        boolean gestor = u.permisos().contains(GESTIONAR);
        var datos = mercados.catalogo(u, c);
        Pantalla p = new Pantalla(titulo, "Catálogo de gestión de puestos municipales.", BASE);
        if (editar != null || r.getParameter("nuevo") != null) Validacion.permiso(u, GESTIONAR);
        navegacion(p, c, "");
        if (editar != null || r.getParameter("nuevo") != null) {
            var entrada = editar == null ? null : datos.stream().filter(x -> x.id() == editar).findFirst().orElseThrow(
                    () -> new IllegalArgumentException("El registro no está disponible."));
            p.formularios().add(new Formulario(entrada == null ? "Nuevo registro" : "Editar nombre", "Guardar", "catalogo", "",
                    List.of(Campo.oculto("catalogo", c.name()), Campo.oculto("editar", editar),
                            entrada == null ? Campo.texto("codigo", "Código", "", true, 35) : Campo.oculto("codigo", ""),
                            Campo.texto("nombre", "Nombre", entrada == null ? "" : entrada.nombre(), true,
                                    c == Catalogo.MERCADOS ? 140 : 100))));
            p.enlaces().add(new Enlace("Cancelar", rutaCatalogo));
        } else if (gestor) {
            p.enlaces().add(new Enlace(c == Catalogo.MERCADOS ? "Registrar mercado" : "Registrar sector", rutaCatalogo + "&nuevo=1"));
        }
        p.tablas().add(new Tabla(c == Catalogo.MERCADOS ? "Catálogo de mercados" : "Catálogo de sectores",
                List.of("Código", c == Catalogo.MERCADOS ? "Nombre del mercado" : "Nombre del sector"), datos.stream()
                .map(x -> Fila.nueva(gestor ? new Enlace("Editar", rutaCatalogo + "&editar=" + x.id()) : null,
                        x.codigo(), x.nombre())).toList(), gestor));
        return p;
    }

    private Pantalla detalle(HttpServletRequest r, Identidad u, Puesto puesto) throws SQLException {
        String uri = BASE + "?id=" + puesto.id();
        Pantalla p = new Pantalla("Puesto " + puesto.numero(), puesto.mercado() + " · " + puesto.sector(), BASE);
        p.enlaces().add(new Enlace("Volver a puestos", BASE));
        p.tablas().add(new Tabla("Disponibilidad del puesto", List.of("Estado", "Comerciante de la asignación vigente"),
                List.of(Fila.texto(puesto.ocupado() ? "Ocupado" : "Libre", puesto.comerciante())), false));
        var historial = mercados.historial(u, puesto.id(), Pagina.numero(r.getParameter("pagina")));
        p.tablas().add(tabla("Historial de asignaciones", List.of("Comerciante", "Giro", "Asignación", "Liberación"),
                historial.filas().stream().map(a -> Fila.texto(a.comerciante(), a.giro(), fecha(a.asignado()),
                        fecha(a.liberado()))).toList(), false, historial, BASE, Map.of("id", puesto.id())));
        if (u.permisos().contains(GESTIONAR)) {
            if (puesto.ocupado()) {
                p.formularios().add(new Formulario("Asignación vigente", "Liberar puesto", "liberar",
                        "El puesto quedará libre y su asignación se conservará en el historial.",
                        List.of(Campo.oculto("id", puesto.id()), Campo.oculto("asignacion", puesto.asignacion()))));
            } else {
                String q = dato(r, "buscar");
                p.filtros(new Formulario("", "Buscar comerciante", "", "", List.of(Campo.oculto("id", puesto.id()),
                        Campo.texto("buscar", "Nombre o documento", q, false, 180))));
                p.formularios().add(new Formulario("Asignar puesto", "Confirmar asignación", "asignar",
                        "El puesto quedará ocupado por el contribuyente seleccionado.", List.of(Campo.oculto("id", puesto.id()),
                        Campo.seleccionar("contribuyente", "Comerciante vigente", "", true, mercados.contribuyentes(u, q)),
                        Campo.texto("giro", "Giro comercial", "", true, 140))));
            }
        }
        return p;
    }

    @Override
    protected Cambio operar(HttpServletRequest r, Identidad u) throws SQLException {
        return switch (dato(r, "op")) {
            case "alta" -> {
                long id = mercados.altaPuesto(u, Validacion.id(dato(r, "mercado"), "mercado"),
                        Validacion.id(dato(r, "sector"), "sector"), dato(r, "numero"));
                yield new Cambio(BASE + "?id=" + id, "Se registró el puesto.");
            }
            case "catalogo" -> {
                Catalogo c = Validacion.opcion(Catalogo.class, dato(r, "catalogo"), "catálogo");
                mercados.guardarCatalogo(u, c, Validacion.idOpcional(dato(r, "editar"), "registro"),
                        dato(r, "codigo"), dato(r, "nombre"));
                yield new Cambio(BASE + "?catalogo=" + c.name(), "Se guardó el registro.");
            }
            case "asignar" -> {
                long id = Validacion.id(dato(r, "id"), "puesto");
                mercados.asignar(u, id, Validacion.id(dato(r, "contribuyente"), "comerciante"), dato(r, "giro"));
                yield new Cambio(BASE + "?id=" + id, "Se asignó el puesto.");
            }
            case "liberar" -> {
                long id = Validacion.id(dato(r, "id"), "puesto");
                mercados.liberar(u, id, Validacion.id(dato(r, "asignacion"), "asignación"));
                yield new Cambio(BASE + "?id=" + id, "Se liberó el puesto.");
            }
            default -> throw new IllegalArgumentException("Operación no disponible.");
        };
    }

    private List<Opcion> catalogo(Identidad u, Catalogo c) throws SQLException {
        return mercados.catalogo(u, c).stream().map(x -> new Opcion(x.id(), x.nombre())).toList();
    }

    private static void navegacion(Pantalla pantalla, Catalogo catalogo, String estado) {
        pantalla.vista("Puestos", BASE, catalogo == null && estado.isEmpty());
        pantalla.vista("Puestos libres", BASE + "?estado=LIBRE", catalogo == null && estado.equals("LIBRE"));
        pantalla.vista("Puestos ocupados", BASE + "?estado=OCUPADO", catalogo == null && estado.equals("OCUPADO"));
        pantalla.vista("Mercados", BASE + "?catalogo=MERCADOS", catalogo == Catalogo.MERCADOS);
        pantalla.vista("Sectores", BASE + "?catalogo=SECTORES", catalogo == Catalogo.SECTORES);
    }

    @Override
    protected String volverTrasError(HttpServletRequest r) {
        String id = dato(r, "id"), c = dato(r, "catalogo");
        if (id.matches("[0-9]{1,18}")) return BASE + "?id=" + id;
        if (c.equals("MERCADOS") || c.equals("SECTORES")) {
            String editar = dato(r, "editar");
            return BASE + "?catalogo=" + c + (editar.matches("[0-9]{1,18}") ? "&editar=" + editar : "&nuevo=1");
        }
        return "alta".equals(dato(r, "op")) ? BASE + "?nuevo=1" : BASE;
    }
}
