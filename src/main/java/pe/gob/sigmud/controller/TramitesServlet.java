package pe.gob.sigmud.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pe.gob.sigmud.dao.impl.TramiteJdbcDao.Detalle;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.ModuloMunicipal;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla;
import pe.gob.sigmud.model.Pantalla.*;
import pe.gob.sigmud.model.Tramite;
import pe.gob.sigmud.model.Tramite.Estado;
import pe.gob.sigmud.service.TramitesService;
import pe.gob.sigmud.util.Validacion;

@WebServlet("/tramites")
public final class TramitesServlet extends ModuloServlet {
    private static final String BASE = "/tramites";
    private static final List<Opcion> ESTADOS = Arrays.stream(Estado.values())
            .map(estado -> new Opcion(estado.name(), estado.etiqueta())).toList();
    private final TramitesService servicio = new TramitesService();

    @Override protected ModuloMunicipal modulo() { return ModuloMunicipal.TRAMITES; }

    @Override
    protected Pantalla vista(HttpServletRequest r, Identidad usuario) throws SQLException {
        Long id = Validacion.idOpcional(dato(r, "id"), "expediente");
        Long persona = Validacion.idOpcional(dato(r, "contribuyente"), "contribuyente");
        if (r.getParameter("nuevo") != null) return registro(r, usuario, persona);
        if (id != null) return detalle(usuario, servicio.consultar(usuario, id));
        return listado(r, usuario, persona);
    }

    private Pantalla listado(HttpServletRequest r, Identidad usuario, Long persona) throws SQLException {
        String codigo = dato(r, "q");
        if (codigo.length() > 40) throw new IllegalArgumentException("El código admite hasta 40 caracteres.");
        String estado = dato(r, "estado");
        String desde = dato(r, "desde");
        String hasta = dato(r, "hasta");
        Long area = Validacion.idOpcional(dato(r, "area"), "área");
        var resultados = servicio.listar(usuario, codigo, persona, area, estado, desde, hasta,
                Pagina.numero(r.getParameter("pagina")));
        String rol = usuario.codigoRol();
        boolean mesa = "MESA_PARTES".equals(rol);
        boolean global = "ADMINISTRADOR".equals(rol);
        String titulo = switch (rol) {
            case "MESA_PARTES" -> "Bandeja de Mesa de Partes";
            case "RENTAS" -> "Expedientes de Rentas";
            case "REGISTRO_CIVIL" -> "Expedientes de Registro Civil";
            case "FISCALIZACION_DEFENSA_CIVIL" -> "Expedientes de Fiscalización y Defensa Civil";
            default -> "Trámite documentario";
        };
        var areas = servicio.areas(usuario, true);
        Pantalla pantalla = new Pantalla(titulo, (mesa ? "Registra ingresos y deriva expedientes de tu bandeja."
                : "Consulta el historial y registra los movimientos autorizados.")
                + (areas.size() == 1 ? " Área actual: " + areas.get(0).etiqueta() + "." : ""), BASE);
        pantalla.estadoVacio(global ? "No hay expedientes que coincidan con estos filtros."
                : "No hay expedientes en tu ámbito que coincidan con estos filtros. Revisa el estado o el intervalo de fechas.");
        String alcance = persona == null ? "" : "&contribuyente=" + persona;
        pantalla.vista("Todos", BASE + "?estado=" + alcance, estado.isEmpty());
        pantalla.vista("Recibidos", BASE + "?estado=RECIBIDO" + alcance, estado.equals("RECIBIDO"));
        pantalla.vista("En trámite", BASE + "?estado=EN_TRAMITE" + alcance, estado.equals("EN_TRAMITE"));
        pantalla.vista("Atendidos", BASE + "?estado=ATENDIDO" + alcance, estado.equals("ATENDIDO"));
        if (usuario.permisos().contains("TRAMITES_REGISTRAR")) {
            pantalla.enlaces().add(new Enlace("Registrar expediente", BASE + "?nuevo=1"
                    + (persona == null ? "" : "&contribuyente=" + persona)));
        }
        if (persona != null) pantalla.enlaces().add(new Enlace("Descargar historial del contribuyente",
                BASE + "?exportar=1&contribuyente=" + persona));
        pantalla.filtros(new Formulario("", "Filtrar", "", "", List.of(
                Campo.oculto("contribuyente", persona),
                Campo.texto("q", "Número de expediente", codigo, false, 40),
                Campo.seleccionar("estado", "Estado actual", estado, false, ESTADOS),
                areas.size() > 1 ? Campo.seleccionar("area", "Área actual", area, false, areas) : Campo.oculto("area", area),
                Campo.tipo("desde", "Ingreso desde", "date", desde, false), Campo.tipo("hasta", "Ingreso hasta", "date", hasta, false))));
        Map<String, Object> filtros = new LinkedHashMap<>(Map.of("q", codigo, "estado", estado, "desde", desde, "hasta", hasta));
        if (persona != null) filtros.put("contribuyente", persona);
        if (area != null) filtros.put("area", area);
        pantalla.tablas().add(tabla(mesa ? "Expedientes de la bandeja" : "Expedientes del ámbito consultado",
                List.of("Expediente", "Tipo de trámite", "Área actual", "Estado actual", "Fecha de ingreso"),
                resultados.filas().stream().map(t -> Fila.nueva(new Enlace("Seguimiento", BASE + "?id=" + t.id()),
                        t.codigo(), t.tipo(), t.area(), t.estado().etiqueta(), fecha(t.registrado()))).toList(),
                true, resultados, BASE, filtros));
        return pantalla;
    }

    private Pantalla registro(HttpServletRequest r, Identidad usuario, Long persona) throws SQLException {
        Validacion.permiso(usuario, "TRAMITES_REGISTRAR");
        String buscar = dato(r, "buscar");
        if (buscar.length() > 180) throw new IllegalArgumentException("Acorta la búsqueda de contribuyente.");
        Pantalla pantalla = new Pantalla("Registrar expediente", "Selecciona un solicitante con ficha vigente y el área de destino.", BASE);
        pantalla.enlaces().add(new Enlace("Cancelar", BASE));
        pantalla.filtros(new Formulario("Localizar al solicitante", "Buscar solicitante", "", "", List.of(
                Campo.oculto("nuevo", 1), Campo.oculto("contribuyente", persona),
                Campo.texto("buscar", "Nombre o documento", buscar, false, 180))));
        pantalla.formularios().add(new Formulario("Datos del expediente", "Registrar expediente", "registrar", "", List.of(
                Campo.seleccionar("contribuyente", "Solicitante con ficha vigente", persona, true, servicio.contribuyentes(usuario, buscar, persona)),
                Campo.seleccionar("area", "Área de destino", "", true, servicio.areas(usuario, false)),
                Campo.sugerencias("tipo", "Tipo de trámite", "", 120, servicio.tipos(usuario)),
                Campo.parrafo("descripcion", "Descripción", "", true, 4000))));
        return pantalla;
    }

    private Pantalla detalle(Identidad usuario, Detalle detalle) throws SQLException {
        Tramite t = detalle.tramite();
        Pantalla pantalla = new Pantalla(t.codigo(), "Seguimiento del expediente", BASE);
        pantalla.enlaces().add(new Enlace("Volver a expedientes", BASE));
        pantalla.enlaces().add(new Enlace("Descargar historial", BASE + "?id=" + t.id() + "&exportar=1"));
        if (usuario.permisos().contains("CONTRIBUYENTES_CONSULTAR")) {
            pantalla.enlaces().add(new Enlace("Ficha del contribuyente", "/contribuyentes?id=" + t.contribuyente()));
        }
        pantalla.tablas().add(new Tabla("Datos del expediente", List.of("Campo", "Valor"), List.of(
                Fila.texto("Solicitante", t.titular()), Fila.texto("Tipo", t.tipo()),
                Fila.texto("Descripción", t.descripcion()), Fila.texto("Estado", t.estado().etiqueta()),
                Fila.texto("Área actual", t.area()), Fila.texto("Fecha de registro", fecha(t.registrado()))), false));
        pantalla.tablas().add(new Tabla("Historial de movimientos", List.of("Fecha", "Área", "Estado", "Registrado por", "Observación"),
                detalle.movimientos().stream().map(m -> Fila.texto(fecha(m.fecha()), m.area(), m.estado().etiqueta(),
                        m.operador(), m.observacion())).toList(), false));
        if (t.estado() != Estado.ARCHIVADO && usuario.permisos().contains("TRAMITES_ACTUALIZAR")) {
            pantalla.formularios().add(new Formulario("Registrar movimiento", "Guardar movimiento", "actualizar",
                    "Se agregará un movimiento al historial del expediente.", List.of(Campo.oculto("id", t.id()),
                    Campo.oculto("version", t.version()), Campo.seleccionar("area", "Área de destino", t.areaId(), true,
                    servicio.areas(usuario, false)), Campo.seleccionar("estado", "Estado", t.estado().name(), true, ESTADOS),
                    Campo.parrafo("observacion", "Observación", "", false, 500))));
        }
        return pantalla;
    }

    @Override
    protected Cambio operar(HttpServletRequest r, Identidad usuario) throws SQLException {
        long area = Validacion.id(dato(r, "area"), "área");
        return switch (dato(r, "op")) {
            case "registrar" -> {
                String codigo = servicio.registrar(usuario, Validacion.id(dato(r, "contribuyente"), "contribuyente"),
                        area, dato(r, "tipo"), dato(r, "descripcion"));
                yield new Cambio(BASE, "Expediente registrado: " + codigo);
            }
            case "actualizar" -> {
                servicio.actualizar(usuario, Validacion.id(dato(r, "id"), "expediente"),
                        Validacion.id(dato(r, "version"), "movimiento"), area, dato(r, "estado"), dato(r, "observacion"));
                yield new Cambio(BASE, "Se agregó el movimiento al historial.");
            }
            default -> throw new IllegalArgumentException("Selecciona una operación válida.");
        };
    }

    @Override
    protected boolean descargar(HttpServletRequest r, HttpServletResponse respuesta, Identidad usuario)
            throws IOException, SQLException {
        if (r.getParameter("exportar") == null) return false;
        String id = dato(r, "id");
        List<Detalle> resultados = id.isEmpty() ? servicio.historial(usuario,
                Validacion.id(dato(r, "contribuyente"), "contribuyente")) : List.of(servicio.consultar(usuario,
                        Validacion.id(id, "expediente")));
        respuesta.setContentType("text/plain;charset=UTF-8");
        respuesta.setHeader("Content-Disposition", "attachment; filename=\"historial-tramites.txt\"");
        var salida = respuesta.getWriter();
        salida.println("SIGMUD · Historial de trámites");
        for (Detalle detalle : resultados) {
            Tramite t = detalle.tramite();
            salida.println();
            salida.printf("%s · %s · %s%n", t.codigo(), t.tipo(), t.titular());
            for (var m : detalle.movimientos()) {
                salida.printf("%s | %s | %s | %s%n", fecha(m.fecha()), m.area(), m.estado().etiqueta(),
                        m.observacion() == null ? "" : m.observacion());
            }
        }
        return true;
    }

    @Override
    protected String volverTrasError(HttpServletRequest r) {
        String id = dato(r, "id");
        return id.matches("[0-9]{1,18}") ? BASE + "?id=" + id : BASE + "?nuevo=1";
    }

}
