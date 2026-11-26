package pe.gob.sigmud.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import pe.gob.sigmud.model.CasoMunicipal;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.ModuloMunicipal;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla;
import pe.gob.sigmud.model.Pantalla.*;
import pe.gob.sigmud.service.CasosService;
import pe.gob.sigmud.util.Validacion;

@WebServlet("/reclamos")
public final class ReclamosServlet extends ModuloServlet {
    private static final String BASE = "/reclamos";
    private final CasosService casos = new CasosService();

    @Override protected ModuloMunicipal modulo() { return ModuloMunicipal.RECLAMOS; }

    @Override
    protected Pantalla vista(HttpServletRequest r, Identidad u) throws SQLException {
        Long id = Validacion.idOpcional(dato(r, "id"), "caso");
        if (r.getParameter("nuevo") != null) return alta(r, u);
        if (id != null) return detalle(u, casos.detalle(u, id));
        String codigo = dato(r, "q"), tipo = dato(r, "tipo"), prioridad = dato(r, "prioridad");
        String estado = r.getParameter("estado") == null ? "ABIERTOS" : dato(r, "estado");
        String area = dato(r, "area"), desde = dato(r, "desde"), hasta = dato(r, "hasta");
        var filas = casos.listar(u, codigo, tipo, prioridad, estado, Validacion.idOpcional(area, "área"),
                desde, hasta, Pagina.numero(r.getParameter("pagina")));
        boolean defensa = "FISCALIZACION_DEFENSA_CIVIL".equals(u.codigoRol());
        boolean atencion = "ATENCION_VECINO".equals(u.codigoRol());
        List<Opcion> areas = casos.areas(u);
        String titulo = defensa ? "Cola de respuesta" : atencion ? "Solicitudes de vecinos" : "Reclamos y emergencias";
        Pantalla p = new Pantalla(titulo, defensa ? "Prioriza incidencias en Fiscalización y Defensa Civil."
                : atencion ? "Da seguimiento a los casos recibidos por Atención al Vecino."
                : "Consulta casos municipales por área, estado y prioridad.", BASE);
        p.estadoVacio("No hay casos que coincidan con estos filtros dentro de tu ámbito.");
        p.vista("Casos abiertos", BASE + "?estado=ABIERTOS", "ABIERTOS".equals(estado) && tipo.isEmpty() && prioridad.isEmpty());
        if (defensa) {
            p.vista("Emergencias abiertas", BASE + "?estado=ABIERTOS&tipo=EMERGENCIA",
                    "ABIERTOS".equals(estado) && "EMERGENCIA".equals(tipo));
            p.vista("Alta o crítica", BASE + "?estado=ABIERTOS&prioridad=" + CasosService.PRIORIDAD_URGENTE,
                    "ABIERTOS".equals(estado) && CasosService.PRIORIDAD_URGENTE.equals(prioridad));
        }
        if (atencion) p.vista("En atención", BASE + "?estado=EN_ATENCION", "EN_ATENCION".equals(estado));
        p.vista("Atendidos", BASE + "?estado=ATENDIDO", "ATENDIDO".equals(estado));
        p.vista("Todos", BASE + "?estado=", estado.isEmpty());
        if (u.permisos().contains("RECLAMOS_REGISTRAR")) {
            p.enlaces().add(new Enlace(atencion ? "Registrar solicitud" : "Registrar caso", BASE + "?nuevo=1"));
        }
        List<Opcion> prioridades = new ArrayList<>(opciones(CasosService.PRIORIDADES));
        prioridades.add(new Opcion(CasosService.PRIORIDAD_URGENTE, "Alta o crítica"));
        List<Opcion> estados = new ArrayList<>(opciones(CasosService.ESTADOS));
        estados.add(0, new Opcion("ABIERTOS", "Abiertos"));
        p.filtros(new Formulario("", "Filtrar", "", "", List.of(Campo.texto("q", atencion ? "Código de solicitud" : "Código del caso", codigo, false, 40),
                Campo.seleccionar("tipo", "Tipo", tipo, false, opciones(CasosService.TIPOS)),
                Campo.seleccionar("prioridad", "Prioridad", prioridad, false, prioridades),
                Campo.seleccionar("estado", "Estado de atención", estado, false, estados),
                areas.size() > 1 ? Campo.seleccionar("area", "Área", area, false, areas) : Campo.oculto("area", area),
                Campo.tipo("desde", "Desde", "date", desde, false), Campo.tipo("hasta", "Hasta", "date", hasta, false))));
        List<String> columnas = atencion ? List.of("Código", "Tipo", "Estado", "Prioridad", "Última atención", "Registro")
                : List.of("Código", "Tipo", "Prioridad", "Estado", "Área", "Registro");
        p.tablas().add(tabla(atencion ? "Seguimiento de solicitudes" : "Cola de atención", columnas,
                filas.filas().stream().map(c -> {
                    Enlace enlace = new Enlace(atencion ? "Ver seguimiento" : "Consultar", BASE + "?id=" + c.id());
                    return atencion ? Fila.nueva(enlace, c.codigo(), etiqueta(c.tipo()), etiqueta(c.estado()),
                            etiqueta(c.prioridad()), fecha(c.atencion()), fecha(c.registro()))
                            : Fila.nueva(enlace, c.codigo(), etiqueta(c.tipo()), etiqueta(c.prioridad()),
                                    etiqueta(c.estado()), c.area(), fecha(c.registro()));
                }).toList(),
                true, filas, BASE, Map.of("q", codigo, "tipo", tipo, "prioridad", prioridad, "estado", estado, "area", area,
                        "desde", desde, "hasta", hasta)));
        r.setAttribute("colaAutomatica", true);
        return p;
    }

    private Pantalla alta(HttpServletRequest r, Identidad u) throws SQLException {
        Validacion.permiso(u, "RECLAMOS_REGISTRAR");
        boolean atencion = "ATENCION_VECINO".equals(u.codigoRol());
        String titulo = atencion ? "Registrar solicitud" : "Registrar caso";
        Pantalla p = new Pantalla(titulo, atencion ? "Registra la solicitud del vecino y su prioridad."
                : "Clasifica el reclamo o la emergencia y asigna su prioridad.", BASE);
        p.enlaces().add(new Enlace("Cancelar", BASE));
        var areas = casos.areas(u);
        List<Campo> campos = new ArrayList<>(List.of(
                Campo.seleccionar("tipo", "Tipo de caso", "RECLAMO", true, opciones(CasosService.TIPOS)),
                Campo.seleccionar("prioridad", "Prioridad", "MEDIA", true, opciones(CasosService.PRIORIDADES)),
                Campo.seleccionar("area", "Área responsable", areas.size() == 1 ? areas.get(0).valor() : "", true, areas),
                Campo.texto("ubicacion", "Referencia de ubicación", "", false, 220),
                Campo.parrafo("descripcion", "Descripción", "", true, 4000)));
        if (u.permisos().contains("CONTRIBUYENTES_CONSULTAR")) {
            String buscar = dato(r, "buscar");
            Validacion.texto(buscar, "búsqueda", 180, false);
            p.filtros(new Formulario("", "Buscar contribuyente", "", "", List.of(Campo.oculto("nuevo", 1),
                    Campo.texto("buscar", "Nombre o documento", buscar, false, 180))));
            campos.add(3, Campo.seleccionar("contribuyente", atencion ? "Vecino asociado (opcional)" : "Persona o entidad asociada (opcional)",
                    "", false, casos.contribuyentes(u, buscar)));
        }
        p.formularios().add(new Formulario(atencion ? "Datos de la solicitud" : "Datos del caso", titulo, "registrar", "", campos));
        return p;
    }

    private Pantalla detalle(Identidad u, CasoMunicipal c) throws SQLException {
        Pantalla p = new Pantalla(c.codigo(), etiqueta(c.tipo()) + " · " + etiqueta(c.estado()), BASE);
        boolean atencion = "ATENCION_VECINO".equals(u.codigoRol());
        p.enlaces().add(new Enlace(atencion ? "Volver a solicitudes" : "Volver a la cola", BASE));
        p.tablas().add(new Tabla(atencion ? "Detalle de la solicitud" : "Detalle del caso", List.of("Campo", "Valor"), List.of(
                Fila.texto("Área responsable", c.area()), Fila.texto("Prioridad", etiqueta(c.prioridad())),
                Fila.texto("Contribuyente", c.titular()), Fila.texto("Ubicación", c.ubicacion()),
                Fila.texto("Descripción", c.descripcion()), Fila.texto("Registro", fecha(c.registro())),
                Fila.texto("Atención", c.atencion() == null ? null : fecha(c.atencion()))), false));
        if (c.abierto() && u.permisos().contains("RECLAMOS_ATENDER")) {
            p.formularios().add(new Formulario("Actualizar atención", "Guardar atención", "atender",
                    "Se actualizarán el estado, la prioridad y el área responsable.", List.of(
                    Campo.oculto("id", c.id()), Campo.oculto("areaAnterior", c.areaId()),
                    Campo.oculto("estadoAnterior", c.estado()), Campo.oculto("prioridadAnterior", c.prioridad()),
                    Campo.seleccionar("area", "Área responsable", c.areaId(), true, casos.areas(u)),
                    Campo.seleccionar("prioridad", "Prioridad", c.prioridad(), true, opciones(CasosService.PRIORIDADES)),
                    Campo.seleccionar("estado", "Estado", c.estado(), true, opciones(CasosService.ESTADOS)))));
        }
        return p;
    }

    @Override
    protected Cambio operar(HttpServletRequest r, Identidad u) throws SQLException {
        long area = Validacion.id(dato(r, "area"), "área");
        if ("registrar".equals(dato(r, "op"))) {
            Long persona = Validacion.idOpcional(dato(r, "contribuyente"), "contribuyente");
            if (persona != null) Validacion.permiso(u, "CONTRIBUYENTES_CONSULTAR");
            long id = casos.registrar(u, dato(r, "tipo"), dato(r, "prioridad"), area,
                    persona, dato(r, "descripcion"), dato(r, "ubicacion"));
            return new Cambio(BASE + "?id=" + id, "Se registró el caso.");
        }
        if (!"atender".equals(dato(r, "op"))) throw new IllegalArgumentException("Operación no disponible.");
        long id = Validacion.id(dato(r, "id"), "caso");
        casos.atender(u, id, area, dato(r, "estado"), dato(r, "prioridad"),
                Validacion.id(dato(r, "areaAnterior"), "área anterior"), dato(r, "estadoAnterior"), dato(r, "prioridadAnterior"));
        return new Cambio(BASE + "?id=" + id, "Se guardó la atención.");
    }

    @Override
    protected String volverTrasError(HttpServletRequest r) {
        String id = dato(r, "id");
        return id.matches("[0-9]{1,18}") ? BASE + "?id=" + id : BASE + "?nuevo=1";
    }
}
