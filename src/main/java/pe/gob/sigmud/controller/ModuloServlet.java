package pe.gob.sigmud.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.ModuloMunicipal;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla;
import pe.gob.sigmud.model.Pantalla.Campo;
import pe.gob.sigmud.model.Pantalla.Enlace;
import pe.gob.sigmud.model.Pantalla.Formulario;
import pe.gob.sigmud.model.Pantalla.Fila;
import pe.gob.sigmud.model.Pantalla.Opcion;
import pe.gob.sigmud.model.Pantalla.Tabla;
import pe.gob.sigmud.util.SeguridadWeb;

public abstract class ModuloServlet extends HttpServlet {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm", Locale.ROOT);

    protected abstract ModuloMunicipal modulo();
    protected abstract Pantalla vista(HttpServletRequest request, Identidad usuario) throws SQLException;
    protected abstract Cambio operar(HttpServletRequest request, Identidad usuario) throws SQLException;

    protected record Cambio(String destino, String mensaje) {}

    private record ErrorFormulario(String destino, String operacion, String mensaje, Map<String, String> datos) {}

    @Override
    protected final void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            Identidad usuario = usuario(request);
            if (descargar(request, response, usuario)) {
                return;
            }
            String clave = "aviso" + modulo().name();
            request.setAttribute("exito", request.getSession(false).getAttribute(clave));
            request.getSession(false).removeAttribute(clave);
            request.setAttribute("moduloActual", modulo());
            Pantalla pantalla = vista(request, usuario);
            Object error = request.getSession(false).getAttribute(clave + "Error");
            if (error instanceof ErrorFormulario formulario && formulario.destino().equals(destinoActual(request))) {
                request.getSession(false).removeAttribute(clave + "Error");
                request.setAttribute("error", formulario.mensaje());
                pantalla.formularios().replaceAll(form -> recuperarEntrada(form, formulario));
            }
            request.setAttribute("pantalla", pantalla);
            request.getRequestDispatcher("/WEB-INF/views/gestion/modulo.jsp").forward(request, response);
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("error", e.getMessage());
            request.setAttribute("moduloActual", modulo());
            Pantalla pantalla = new Pantalla(modulo().titulo(), "", modulo().ruta());
            pantalla.enlaces().add(new Enlace("Volver al listado", modulo().ruta()));
            request.setAttribute("pantalla", pantalla);
            request.getRequestDispatcher("/WEB-INF/views/gestion/modulo.jsp").forward(request, response);
        } catch (SecurityException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        } catch (SQLException e) {
            falloSql(response, e);
        }
    }

    @Override
    protected final void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String clave = "aviso" + modulo().name();
        try {
            Cambio cambio = operar(request, usuario(request));
            request.getSession(false).setAttribute(clave, cambio.mensaje());
            SeguridadWeb.redirigir(request, response, cambio.destino());
        } catch (IllegalArgumentException e) {
            String destino = volverTrasError(request);
            request.getSession(false).setAttribute(clave + "Error",
                    new ErrorFormulario(destino, dato(request, "op"), e.getMessage(), valoresFormulario(request)));
            SeguridadWeb.redirigir(request, response, destino);
        } catch (SecurityException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        } catch (SQLException e) {
            falloSql(response, e);
        }
    }

    private static String destinoActual(HttpServletRequest request) {
        String consulta = request.getQueryString();
        return request.getServletPath() + (consulta == null ? "" : "?" + consulta);
    }

    private static Map<String, String> valoresFormulario(HttpServletRequest request) {
        Map<String, String> valores = new LinkedHashMap<>();
        request.getParameterMap().entrySet().stream()
                .filter(e -> !List.of("csrf", "op", "contrasena", "password").contains(e.getKey()))
                .filter(e -> e.getKey().length() <= 40 && e.getValue()[0].length() <= 4000)
                .limit(16).forEach(e -> valores.put(e.getKey(), e.getValue()[0]));
        return Map.copyOf(valores);
    }

    private static Formulario recuperarEntrada(Formulario formulario, ErrorFormulario error) {
        if (!formulario.operacion().equals(error.operacion())) return formulario;
        List<Campo> campos = formulario.campos().stream().map(campo -> {
            String dato = error.datos().get(campo.nombre());
            if (dato == null || "hidden".equals(campo.tipo())) return campo;
            if ("select".equals(campo.tipo()) && !dato.isEmpty()
                    && campo.opciones().stream().noneMatch(opcion -> opcion.valor().equals(dato))) return campo;
            return new Campo(campo.nombre(), campo.etiqueta(), campo.tipo(), dato,
                    campo.obligatorio(), campo.longitud(), campo.opciones());
        }).toList();
        return new Formulario(formulario.titulo(), formulario.boton(), formulario.operacion(), formulario.confirmar(), campos);
    }

    protected boolean descargar(HttpServletRequest request, HttpServletResponse response, Identidad usuario)
            throws IOException, SQLException {
        return false;
    }

    protected String volverTrasError(HttpServletRequest request) {
        return modulo().ruta();
    }

    private void falloSql(HttpServletResponse response, SQLException e) throws IOException {
        getServletContext().log("SIGMUD: operación JDBC de " + modulo().name() + ". Código " + e.getErrorCode());
        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }

    private Identidad usuario(HttpServletRequest request) {
        Identidad identidad = (Identidad) request.getAttribute("identidad");
        if (!modulo().permitido(identidad)) {
            throw new SecurityException("No tienes autorización.");
        }
        return identidad;
    }

    protected static String dato(HttpServletRequest request, String nombre) {
        String valor = request.getParameter(nombre);
        return valor == null ? "" : valor.strip();
    }

    protected static String fecha(LocalDateTime valor) { return valor == null ? null : valor.format(FECHA); }

    protected static String etiqueta(String codigo) {
        return switch (codigo) {
            case "CRITICA" -> "Crítica";
            case "EN_ATENCION" -> "En atención";
            case "NO_ASISTIO" -> "No asistió";
            case "JURIDICA" -> "Jurídica";
            default -> codigo.substring(0, 1) + codigo.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    protected static List<Opcion> opciones(List<String> valores) {
        return valores.stream().map(valor -> new Opcion(valor, etiqueta(valor))).toList();
    }

    protected static String ruta(String base, Map<String, ?> parametros) {
        String consulta = parametros.entrySet().stream().filter(par -> par.getValue() != null)
                .map(par -> codificar(par.getKey()) + "=" + codificar(par.getValue().toString()))
                .collect(Collectors.joining("&"));
        return consulta.isBlank() ? base : base + "?" + consulta;
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    protected static Tabla tabla(String titulo, List<String> columnas, List<Fila> filas, boolean acciones,
            Pagina<?> pagina, String base, Map<String, ?> filtros) {
        return new Tabla(titulo, columnas, filas, acciones,
                pagina.numero() > 1 ? new Enlace("Anterior", rutaPagina(base, filtros, pagina.numero() - 1)) : null,
                pagina.siguiente() ? new Enlace("Siguiente", rutaPagina(base, filtros, pagina.numero() + 1)) : null,
                pagina.numero());
    }

    private static String rutaPagina(String base, Map<String, ?> filtros, int numero) {
        Map<String, Object> parametros = new LinkedHashMap<>(filtros);
        parametros.put("pagina", numero);
        return ruta(base, parametros);
    }
}
