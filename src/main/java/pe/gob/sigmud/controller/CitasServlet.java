package pe.gob.sigmud.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import pe.gob.sigmud.model.CitaMunicipal;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.ModuloMunicipal;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla;
import pe.gob.sigmud.model.Pantalla.*;
import pe.gob.sigmud.service.CitasService;
import pe.gob.sigmud.util.Validacion;

@WebServlet("/citas")
public final class CitasServlet extends ModuloServlet {
    private static final String BASE = "/citas";
    private static final String GESTIONAR = "CITAS_GESTIONAR";
    private static final DateTimeFormatter ENTRADA = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final Locale PERU = Locale.forLanguageTag("es-PE");
    private final CitasService citas = new CitasService();

    public record DiaAgenda(String etiqueta, String numero, String destino, boolean seleccionado) {}
    public record SemanaAgenda(String titulo, String anterior, String siguiente, String completa, List<DiaAgenda> dias) {}

    @Override protected ModuloMunicipal modulo() { return ModuloMunicipal.CITAS; }

    @Override
    protected Pantalla vista(HttpServletRequest r, Identidad u) throws SQLException {
        Long id = Validacion.idOpcional(dato(r, "id"), "cita");
        if (r.getParameter("disponibilidad") != null) return verificar(r, u);
        if (r.getParameter("reprogramar") != null) {
            Validacion.permiso(u, GESTIONAR);
            return programar(r, u, citas.detalle(u, Validacion.id(dato(r, "id"), "cita")));
        }
        if (r.getParameter("nuevo") != null) {
            Validacion.permiso(u, GESTIONAR);
            return programar(r, u, null);
        }
        if (id != null) return detalle(u, citas.detalle(u, id));
        boolean civil = "REGISTRO_CIVIL".equals(u.codigoRol());
        boolean atencion = "ATENCION_VECINO".equals(u.codigoRol());
        String codigo = dato(r, "q"), area = dato(r, "area");
        String estado = (civil || atencion) && r.getParameter("estado") == null ? "PROGRAMADA" : dato(r, "estado");
        String funcionario = dato(r, "funcionario"), desde = dato(r, "desde"), hasta = dato(r, "hasta");
        Map<String, Object> filtros = new LinkedHashMap<>(Map.of("q", codigo, "estado", estado, "area", area,
                "funcionario", funcionario));
        LocalDate semana = null;
        LocalDate dia = null;
        if (r.getParameter("semana") != null) {
            LocalDate referencia = Validacion.fecha(dato(r, "semana"), "semana");
            if (referencia == null) referencia = LocalDate.now(ZoneId.of("America/Lima"));
            semana = referencia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate domingo = semana.plusDays(6);
            if (semana.getYear() < 1000 || domingo.getYear() > 9999) {
                throw new IllegalArgumentException("Selecciona una semana válida.");
            }
            dia = Validacion.fecha(dato(r, "dia"), "día");
            if (dia != null && (dia.isBefore(semana) || dia.isAfter(domingo))) {
                throw new IllegalArgumentException("El día debe pertenecer a la semana consultada.");
            }
            r.setAttribute("semanaAgenda", semanaAgenda(semana, dia, filtros));
            filtros.put("semana", semana);
            if (dia != null) filtros.put("dia", dia);
            desde = (dia == null ? semana : dia).toString();
            hasta = (dia == null ? domingo : dia).toString();
        } else {
            filtros.put("desde", desde);
            filtros.put("hasta", hasta);
        }
        var resultados = citas.agenda(u, codigo, estado, Validacion.idOpcional(area, "área"),
                Validacion.idOpcional(funcionario, "funcionario"), desde, hasta, Pagina.numero(r.getParameter("pagina")));
        List<Opcion> areas = citas.areas(u);
        String titulo = switch (u.codigoRol()) {
            case "RENTAS" -> "Agenda de Rentas";
            case "REGISTRO_CIVIL" -> "Agenda de Registro Civil";
            case "ATENCION_VECINO" -> "Agenda de atención al vecino";
            case "FISCALIZACION_DEFENSA_CIVIL" -> "Agenda de Fiscalización y Defensa Civil";
            default -> "Citas municipales";
        };
        Pantalla p = new Pantalla(titulo,
                civil ? "Consulta los horarios de atención de Registro Civil y organiza las reservas."
                        : "Consulta horarios y reservas por ventanilla dentro de tu ámbito.", BASE);
        p.estadoVacio("No se encontraron citas en tu ámbito con estos filtros. Puedes consultar todos los estados o cambiar las fechas.");
        p.vista("Programadas", BASE + "?estado=PROGRAMADA", semana == null && estado.equals("PROGRAMADA"));
        p.vista("Atendidas", BASE + "?estado=ATENDIDA", semana == null && estado.equals("ATENDIDA"));
        p.vista("Todos los estados", BASE + "?estado=", semana == null && estado.isEmpty());
        p.vista("Calendario semanal", BASE + "?semana=&estado=PROGRAMADA", semana != null);
        var funcionariosActivos = citas.funcionarios(u, null);
        if (funcionariosActivos.isEmpty()) {
            r.setAttribute("notaModulo", "La consulta de citas está disponible. Para agendar se necesita una ventanilla con un funcionario activo habilitado.");
        } else {
            if (u.permisos().contains(GESTIONAR)) p.enlaces().add(new Enlace("Programar cita", BASE + "?nuevo=1"));
            p.enlaces().add(new Enlace("Comprobar disponibilidad", BASE + "?disponibilidad=1"));
        }
        var funcionariosAgenda = citas.funcionariosAgenda(u);
        p.filtros(new Formulario("", "Filtrar agenda", "", "", List.of(
                Campo.texto("q", "Código de cita", codigo, false, 40),
                Campo.seleccionar("estado", "Estado", estado, false, opciones(CitasService.ESTADOS)),
                areas.size() > 1 ? Campo.seleccionar("area", "Área", area, false, areas) : Campo.oculto("area", area),
                funcionariosAgenda.size() > 1
                        ? Campo.seleccionar("funcionario", "Funcionario de la cita", funcionario, false, funcionariosAgenda)
                        : Campo.oculto("funcionario", funcionario),
                semana == null ? Campo.tipo("desde", "Desde", "date", desde, false) : Campo.oculto("semana", semana),
                semana == null ? Campo.tipo("hasta", "Hasta", "date", hasta, false) : Campo.oculto("dia", dia))));
        List<String> columnas = new ArrayList<>(List.of("Cita", "Inicio"));
        if (civil || atencion) columnas.add("Fin");
        columnas.add("Ventanilla");
        if (areas.size() > 1) columnas.add("Área de atención");
        if (!civil && !atencion) columnas.add("Funcionario");
        columnas.add("Estado");
        var filas = resultados.filas().stream().map(c -> {
            List<String> datos = new ArrayList<>(List.of(c.codigo(), fecha(c.inicio())));
            if (civil || atencion) datos.add(fecha(c.fin()));
            datos.add(c.ventanilla());
            if (areas.size() > 1) datos.add(c.area());
            if (!civil && !atencion) datos.add(c.funcionario());
            datos.add(etiqueta(c.estado()));
            return Fila.nueva(new Enlace("Ver cita", BASE + "?id=" + c.id()), datos.toArray(String[]::new));
        }).toList();
        p.tablas().add(tabla(civil || atencion ? "Reservas de atención" : "Citas de la agenda", columnas, filas,
                true, resultados, BASE, filtros));
        return p;
    }

    private static SemanaAgenda semanaAgenda(LocalDate lunes, LocalDate dia, Map<String, Object> filtros) {
        LocalDate domingo = lunes.plusDays(6);
        Map<String, Object> consulta = new LinkedHashMap<>(filtros);
        consulta.put("semana", lunes);
        String completa = ruta(BASE, consulta);
        List<DiaAgenda> dias = new ArrayList<>();
        for (LocalDate fecha = lunes; !fecha.isAfter(domingo); fecha = fecha.plusDays(1)) {
            consulta.put("dia", fecha);
            dias.add(new DiaAgenda(fecha.getDayOfWeek().getDisplayName(TextStyle.SHORT, PERU),
                    Integer.toString(fecha.getDayOfMonth()), ruta(BASE, consulta), fecha.equals(dia)));
        }
        consulta.remove("dia");
        consulta.put("semana", lunes.minusWeeks(1));
        String anterior = lunes.minusWeeks(1).getYear() >= 1000 ? ruta(BASE, consulta) : "";
        consulta.put("semana", lunes.plusWeeks(1));
        String siguiente = domingo.plusWeeks(1).getYear() <= 9999 ? ruta(BASE, consulta) : "";
        return new SemanaAgenda(lunes.format(DIA_MES) + " – " + domingo.format(DIA_MES),
                anterior, siguiente, completa, dias);
    }

    private Pantalla programar(HttpServletRequest r, Identidad u, CitaMunicipal anterior) throws SQLException {
        if (anterior != null && !anterior.programada()) throw new IllegalArgumentException("La cita ya está cerrada.");
        String titulo = anterior == null ? "Programar cita" : "Reprogramar cita";
        String buscar = dato(r, "buscar");
        Long ventanilla = Validacion.idOpcional(dato(r, "v"), "ventanilla");
        if (ventanilla == null && anterior != null) ventanilla = anterior.ventanillaId();
        Pantalla p = new Pantalla(titulo, anterior == null ? "Selecciona ventanilla, funcionario y horario."
                : "La cita anterior se cancela únicamente si se confirma la nueva reserva.", BASE);
        p.enlaces().add(new Enlace("Cancelar", anterior == null ? BASE : BASE + "?id=" + anterior.id()));
        List<Opcion> ventanillas = citas.ventanillas(u);
        if (ventanillas.isEmpty()) {
            p.estadoVacio("No hay ventanillas habilitadas en tu ámbito.");
            return p;
        }
        String seleccion = ventanilla == null ? "" : ventanilla.toString();
        List<Campo> preparacion = new ArrayList<>(List.of(
                Campo.oculto(anterior == null ? "nuevo" : "reprogramar", 1),
                Campo.oculto("id", anterior == null ? "" : anterior.id()),
                Campo.seleccionar("v", "Ventanilla de atención", seleccion, true, ventanillas)));
        String rol = u.codigoRol();
        boolean atencionPersonal = "REGISTRO_CIVIL".equals(rol) || "ATENCION_VECINO".equals(rol);
        if (anterior == null) preparacion.add(Campo.texto("buscar",
                atencionPersonal ? "Nombre o documento del solicitante" : "Buscar contribuyente", buscar, false, 180));
        p.filtros(new Formulario("Ventanilla y búsqueda", "Aplicar selección", "", "", preparacion));
        if (ventanilla == null) {
            p.estadoVacio("Selecciona una ventanilla para cargar sus funcionarios habilitados.");
            return p;
        }
        List<Opcion> habilitados = citas.funcionarios(u, ventanilla);
        if (habilitados.isEmpty()) {
            p.estadoVacio("Esta ventanilla no tiene funcionarios activos habilitados. Selecciona otra ventanilla.");
            return p;
        }
        List<Campo> campos = new ArrayList<>();
        if (anterior == null) {
            campos.add(Campo.seleccionar("contribuyente", atencionPersonal ? "Solicitante con ficha vigente" : "Contribuyente vigente",
                    "", true, citas.contribuyentes(u, buscar)));
        } else {
            campos.add(Campo.oculto("id", anterior.id()));
        }
        campos.addAll(List.of(
                Campo.oculto("ventanilla", seleccion),
                Campo.seleccionar("funcionario", "Funcionario habilitado", anterior == null ? "" : anterior.funcionarioId(),
                        true, habilitados),
                Campo.tipo("inicio", "Inicio", "datetime-local", anterior == null ? "" : anterior.inicio().format(ENTRADA), true),
                Campo.tipo("fin", "Fin", "datetime-local", anterior == null ? "" : anterior.fin().format(ENTRADA), true),
                Campo.texto("motivo", "Motivo", anterior == null ? "" : anterior.motivo(), true, 240)));
        p.formularios().add(new Formulario("Datos de la reserva", titulo, "guardar",
                anterior == null ? "" : "Confirma el nuevo horario para reemplazar la reserva anterior.", campos));
        return p;
    }

    private Pantalla verificar(HttpServletRequest r, Identidad u) throws SQLException {
        String v = dato(r, "ventanilla"), f = dato(r, "funcionario"), inicio = dato(r, "inicio"), fin = dato(r, "fin");
        Long ventanilla = Validacion.idOpcional(v, "ventanilla");
        Pantalla p = new Pantalla("Disponibilidad de horario", "Comprueba el horario antes de registrar una reserva.", BASE);
        p.enlaces().add(new Enlace("Volver a la agenda", BASE));
        List<Opcion> ventanillas = citas.ventanillas(u);
        if (ventanillas.isEmpty()) {
            p.estadoVacio("No hay ventanillas habilitadas para consultar disponibilidad en tu ámbito.");
            return p;
        }
        if (ventanilla == null) {
            p.filtros(new Formulario("Elegir ventanilla", "Continuar", "", "", List.of(Campo.oculto("disponibilidad", 1),
                    Campo.seleccionar("ventanilla", "Ventanilla de atención", "", true, ventanillas))));
            return p;
        }
        List<Opcion> habilitados = citas.funcionarios(u, ventanilla);
        p.enlaces().add(new Enlace("Cambiar ventanilla", BASE + "?disponibilidad=1"));
        if (habilitados.isEmpty()) {
            p.estadoVacio("Esta ventanilla no tiene funcionarios activos habilitados.");
            return p;
        }
        String etiquetaVentanilla = ventanillas.stream().filter(o -> o.valor().equals(v))
                .map(Opcion::etiqueta).findFirst().orElse("Horario de la ventanilla");
        p.filtros(new Formulario(etiquetaVentanilla, "Comprobar", "", "", List.of(Campo.oculto("disponibilidad", 1),
                Campo.oculto("ventanilla", v), Campo.seleccionar("funcionario", "Funcionario habilitado", f, true, habilitados),
                Campo.tipo("inicio", "Inicio", "datetime-local", inicio, true), Campo.tipo("fin", "Fin", "datetime-local", fin, true))));
        if (!f.isBlank() && !inicio.isBlank() && !fin.isBlank()) {
            boolean libre = citas.disponible(u, ventanilla, Validacion.id(f, "funcionario"), inicio, fin);
            r.setAttribute(libre ? "exito" : "error", libre ? "El horario está disponible en este momento."
                    : "El intervalo coincide con una reserva existente.");
        }
        return p;
    }

    private Pantalla detalle(Identidad u, CitaMunicipal c) {
        Pantalla p = new Pantalla(c.codigo(), etiqueta(c.estado()), BASE);
        p.enlaces().add(new Enlace("Volver a la agenda", BASE));
        p.tablas().add(new Tabla("Datos de la cita", List.of("Campo", "Valor"), List.of(
                Fila.texto("Contribuyente", c.titular()), Fila.texto("Área", c.area()), Fila.texto("Ventanilla", c.ventanilla()),
                Fila.texto("Funcionario", c.funcionario()), Fila.texto("Inicio", fecha(c.inicio())),
                Fila.texto("Fin", fecha(c.fin())), Fila.texto("Motivo", c.motivo())), false));
        if (c.programada() && u.permisos().contains(GESTIONAR)) {
            p.enlaces().add(new Enlace("Reprogramar", BASE + "?reprogramar=1&id=" + c.id()));
            p.formularios().add(new Formulario("Cerrar cita", "Guardar estado", "finalizar",
                    "Una vez cerrada, la cita permanecerá disponible para consulta.", List.of(Campo.oculto("id", c.id()),
                    Campo.seleccionar("estado", "Estado de cierre", "", true, opciones(CitasService.CIERRES)))));
        }
        return p;
    }

    @Override
    protected Cambio operar(HttpServletRequest r, Identidad u) throws SQLException {
        Long id = Validacion.idOpcional(dato(r, "id"), "cita");
        if ("guardar".equals(dato(r, "op"))) {
            long creada = citas.guardar(u, id, id == null ? Validacion.id(dato(r, "contribuyente"), "contribuyente") : 0,
                    Validacion.id(dato(r, "ventanilla"), "ventanilla"), Validacion.id(dato(r, "funcionario"), "funcionario"),
                    dato(r, "inicio"), dato(r, "fin"), dato(r, "motivo"));
            return new Cambio(BASE + "?id=" + creada, id == null ? "Se programó la cita." : "Se reprogramó la cita.");
        }
        if (!"finalizar".equals(dato(r, "op")) || id == null) throw new IllegalArgumentException("Operación no disponible.");
        citas.finalizar(u, id, dato(r, "estado"));
        return new Cambio(BASE + "?id=" + id, "Se guardó el estado.");
    }

    @Override
    protected String volverTrasError(HttpServletRequest r) {
        String id = dato(r, "id");
        String v = dato(r, "ventanilla");
        String seleccion = v.matches("[0-9]{1,18}") ? "&v=" + v : "";
        if (!id.matches("[0-9]{1,18}")) return BASE + "?nuevo=1" + seleccion;
        return BASE + "?id=" + id + ("guardar".equals(dato(r, "op")) ? "&reprogramar=1" + seleccion : "");
    }
}
