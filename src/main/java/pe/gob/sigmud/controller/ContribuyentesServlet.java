package pe.gob.sigmud.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import pe.gob.sigmud.model.Contribuyente;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.ModuloMunicipal;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla;
import pe.gob.sigmud.model.Pantalla.Campo;
import pe.gob.sigmud.model.Pantalla.Enlace;
import pe.gob.sigmud.model.Pantalla.Fila;
import pe.gob.sigmud.model.Pantalla.Formulario;
import pe.gob.sigmud.model.Pantalla.Opcion;
import pe.gob.sigmud.model.Pantalla.Tabla;
import pe.gob.sigmud.service.ContribuyentesService;
import pe.gob.sigmud.util.Validacion;

@WebServlet("/contribuyentes")
public final class ContribuyentesServlet extends ModuloServlet {
    private static final String BASE = "/contribuyentes";
    private static final String GESTIONAR = "CONTRIBUYENTES_GESTIONAR";
    private static final List<Opcion> TIPOS = List.of(new Opcion("NATURAL", "Persona natural"),
            new Opcion("JURIDICA", "Persona jurídica"));
    private final ContribuyentesService servicio = new ContribuyentesService();

    @Override protected ModuloMunicipal modulo() { return ModuloMunicipal.CONTRIBUYENTES; }

    @Override
    protected Pantalla vista(HttpServletRequest request, Identidad operador) throws SQLException {
        Long id = Validacion.idOpcional(dato(request, "id"), "contribuyente");
        boolean gestor = operador.permisos().contains(GESTIONAR);
        if (request.getParameter("nuevo") != null || request.getParameter("editar") != null) {
            Validacion.permiso(operador, GESTIONAR);
            return formulario(id == null ? null : servicio.consultar(operador, id));
        }
        if (id != null) return detalle(operador, servicio.consultar(operador, id), gestor);

        String texto = dato(request, "q");
        if (texto.length() > 180) throw new IllegalArgumentException("Usa una búsqueda más breve.");
        String rol = operador.codigoRol();
        boolean civil = "REGISTRO_CIVIL".equals(rol);
        boolean rentas = "RENTAS".equals(rol);
        String tipo = civil && request.getParameter("tipo") == null ? "NATURAL" : dato(request, "tipo");
        String estado = request.getParameter("estado") == null ? "activos" : dato(request, "estado");
        int numero = Pagina.numero(request.getParameter("pagina"));
        boolean fichas = civil && "NATURAL".equals(tipo);
        var resultados = servicio.listar(operador, texto, tipo, estado, numero, !fichas);
        String titulo = switch (rol) {
            case "REGISTRO_CIVIL" -> fichas ? "Fichas de personas" : "Consulta general de contribuyentes";
            case "RENTAS" -> "Padrón de contribuyentes";
            case "MERCADOS" -> "Consulta de comerciantes";
            case "MESA_PARTES" -> "Registro de solicitantes";
            case "ATENCION_VECINO" -> "Consulta de vecinos";
            case "FISCALIZACION_DEFENSA_CIVIL" -> "Consulta de personas y entidades";
            default -> "Contribuyentes y vecinos";
        };
        String descripcion = switch (rol) {
            case "REGISTRO_CIVIL" -> fichas ? "Busca personas naturales por nombre, DNI o RUC."
                    : "Consulta personas naturales, personas jurídicas y sus predios vinculados.";
            case "MESA_PARTES" -> "Localiza la ficha del solicitante antes de registrar o consultar sus expedientes.";
            case "ATENCION_VECINO" -> "Localiza la ficha del vecino para consultar sus datos registrados.";
            case "MERCADOS" -> "Consulta la ficha de las personas y empresas que pueden figurar en una asignación.";
            case "FISCALIZACION_DEFENSA_CIVIL" -> "Consulta los datos de personas y entidades vinculadas a la atención municipal.";
            default -> "Consulta el registro municipal por nombre, documento o código de predio.";
        };
        Pantalla pantalla = new Pantalla(titulo, descripcion, BASE);
        pantalla.estadoVacio(fichas
                ? "No se encontraron personas naturales con estos filtros." : "No se encontraron registros con estos filtros.");
        if (civil) {
            pantalla.enlaces().add(new Enlace(fichas ? "Abrir consulta general" : "Volver a fichas de personas",
                    BASE + (fichas ? "?tipo=" : "?tipo=NATURAL")));
        } else if (rentas) {
            pantalla.vista("Padrón completo", BASE + "?tipo=", tipo.isEmpty());
            pantalla.vista("Personas naturales", BASE + "?tipo=NATURAL", tipo.equals("NATURAL"));
            pantalla.vista("Personas jurídicas", BASE + "?tipo=JURIDICA", tipo.equals("JURIDICA"));
            pantalla.enlaces().add(new Enlace("Buscar predios vinculados", "/busquedas?tipo=PREDIOS"));
        }
        if (gestor) pantalla.enlaces().add(new Enlace("Registrar contribuyente", BASE + "?nuevo=1"));
        pantalla.filtros(new Formulario("", "Buscar", "", "", List.of(
                Campo.texto("q", fichas ? "Nombre, DNI o RUC" : "Nombre, documento o código de predio", texto, false, 180),
                fichas ? Campo.oculto("tipo", tipo) : Campo.seleccionar("tipo", "Tipo de persona", tipo, false, TIPOS),
                Campo.seleccionar("estado", "Estado", estado, false,
                        List.of(new Opcion("activos", "Vigentes"), new Opcion("bajas", "Con baja"))))));
        var filas = resultados.filas().stream().map(c -> {
            Enlace enlace = new Enlace(fichas ? "Abrir ficha" : "Consultar", BASE + "?id=" + c.id());
            String vigencia = c.activo() ? "Vigente" : "Con baja";
            return fichas ? Fila.nueva(enlace, c.nombre(), c.documento(), vigencia)
                    : Fila.nueva(enlace, c.nombre(), c.documento(), etiquetaTipo(c), vigencia);
        }).toList();
        List<String> columnas = fichas ? List.of("Persona", "Documento", "Estado")
                : List.of("Nombre o razón social", "Documento", "Tipo", "Estado");
        pantalla.tablas().add(tabla(fichas ? "Personas naturales" : "Registro municipal", columnas,
                filas, true, resultados, BASE, Map.of("q", texto, "tipo", tipo, "estado", estado)));
        return pantalla;
    }

    private Pantalla detalle(Identidad operador, Contribuyente c, boolean gestor) throws SQLException {
        String detalle = BASE + "?id=" + c.id();
        Pantalla pantalla = new Pantalla(c.nombre(), c.tipo() == Contribuyente.Tipo.NATURAL ? "Ficha de persona natural" : "Ficha de persona jurídica", BASE);
        boolean civil = "REGISTRO_CIVIL".equals(operador.codigoRol());
        if (civil && c.tipo() == Contribuyente.Tipo.NATURAL) {
            pantalla.enlaces().add(new Enlace("Volver a fichas de personas", BASE));
        } else {
            pantalla.enlaces().add(new Enlace(civil ? "Volver a consulta general" : "Volver al registro",
                    civil ? BASE + "?tipo=" : BASE));
        }
        if (operador.permisos().contains("TRAMITES_CONSULTAR")) {
            pantalla.enlaces().add(new Enlace("Expedientes", "/tramites?contribuyente=" + c.id()));
        }
        if (c.activo() && operador.permisos().contains("TRAMITES_REGISTRAR")
                && operador.permisos().contains("TRAMITES_CONSULTAR")) {
            pantalla.enlaces().add(new Enlace("Registrar expediente", "/tramites?nuevo=1&contribuyente=" + c.id()));
        }
        var datos = new java.util.ArrayList<>(List.of(Fila.texto("Tipo", etiquetaTipo(c)), Fila.texto("Documento", c.documento()),
                Fila.texto("Teléfono", c.telefono()), Fila.texto("Dirección", c.direccion())));
        if (c.tipo() == Contribuyente.Tipo.NATURAL) {
            datos.add(Fila.texto("Fecha de nacimiento", c.nacimiento() == null ? null : c.nacimiento().toString()));
        }
        datos.add(Fila.texto("Vigencia de la ficha", c.activo() ? "Vigente" : "Con baja"));
        pantalla.tablas().add(new Tabla("Datos de identificación", List.of("Dato", "Valor"), datos, false));
        List<String> predios = servicio.predios(operador, c.id());
        if (!predios.isEmpty() || gestor || "RENTAS".equals(operador.codigoRol())) {
            pantalla.tablas().add(new Tabla("Predios asociados", List.of("Código del predio"),
                    predios.stream().map(Fila::texto).toList(), false));
        }
        if (gestor && c.activo()) {
            pantalla.enlaces().add(new Enlace("Editar datos", detalle + "&editar=1"));
            pantalla.formularios().add(new Formulario("Vincular predio", "Vincular predio", "predio", "",
                    List.of(Campo.oculto("id", c.id()), Campo.texto("predio", "Código del predio", "", true, 40))));
            pantalla.formularios().add(new Formulario("Baja del registro", "Dar de baja", "baja",
                    "La baja conserva los predios y trámites asociados e impide nuevas operaciones con este contribuyente.",
                    List.of(Campo.oculto("id", c.id()))));
        }
        return pantalla;
    }

    private static Pantalla formulario(Contribuyente c) {
        if (c != null && !c.activo()) throw new IllegalArgumentException("Solo se pueden editar contribuyentes vigentes.");
        Pantalla pantalla = new Pantalla(c == null ? "Registrar contribuyente" : "Editar contribuyente",
                "El DNI, el teléfono y la fecha de nacimiento se validan al guardar.", BASE);
        pantalla.enlaces().add(new Enlace("Cancelar", c == null ? BASE : BASE + "?id=" + c.id()));
        pantalla.formularios().add(new Formulario("Datos de identificación", "Guardar", "guardar", "", List.of(
                Campo.oculto("id", c == null ? "" : c.id()),
                Campo.seleccionar("tipo", "Tipo de persona", c == null ? "NATURAL" : c.tipo().name(), true, TIPOS),
                Campo.texto("nombre", "Nombre o razón social", c == null ? "" : c.nombre(), true, 180),
                Campo.texto("dni", "DNI", c == null ? "" : c.dni(), false, 8),
                Campo.texto("ruc", "RUC", c == null ? "" : c.ruc(), false, 11),
                Campo.tipo("telefono", "Teléfono", "tel", c == null ? "" : c.telefono(), false),
                Campo.tipo("nacimiento", "Fecha de nacimiento", "date", c == null ? "" : c.nacimiento(), false),
                Campo.texto("direccion", "Dirección", c == null ? "" : c.direccion(), false, 220))));
        return pantalla;
    }

    @Override
    protected Cambio operar(HttpServletRequest r, Identidad operador) throws SQLException {
        Long id = Validacion.idOpcional(dato(r, "id"), "contribuyente");
        return switch (dato(r, "op")) {
            case "guardar" -> {
                long guardado = servicio.guardar(operador, id == null ? 0 : id, dato(r, "tipo"), dato(r, "dni"),
                        dato(r, "ruc"), dato(r, "nombre"), dato(r, "telefono"), dato(r, "direccion"), dato(r, "nacimiento"));
                yield new Cambio(BASE + "?id=" + guardado, "Se guardaron los datos del contribuyente.");
            }
            case "predio" -> {
                servicio.vincular(operador, Validacion.id(dato(r, "id"), "contribuyente"), dato(r, "predio"));
                yield new Cambio(BASE + "?id=" + id, "Se vinculó el predio.");
            }
            case "baja" -> {
                servicio.baja(operador, Validacion.id(dato(r, "id"), "contribuyente"));
                yield new Cambio(BASE + "?id=" + id, "Se registró la baja del contribuyente.");
            }
            default -> throw new IllegalArgumentException("Selecciona una operación válida.");
        };
    }

    @Override
    protected String volverTrasError(HttpServletRequest r) {
        String id = dato(r, "id");
        if ("guardar".equals(dato(r, "op"))) return id.matches("[0-9]{1,18}") ? BASE + "?id=" + id + "&editar=1" : BASE + "?nuevo=1";
        return id.matches("[0-9]{1,18}") ? BASE + "?id=" + id : BASE;
    }

    private static String etiquetaTipo(Contribuyente c) {
        return c.tipo() == Contribuyente.Tipo.NATURAL ? "Natural" : "Jurídica";
    }
}
