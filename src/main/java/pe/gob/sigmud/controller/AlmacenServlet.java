package pe.gob.sigmud.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import pe.gob.sigmud.dao.impl.AlmacenJdbcDao.Lote;
import pe.gob.sigmud.dao.impl.AlmacenJdbcDao.Material;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.ModuloMunicipal;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.model.Pantalla;
import pe.gob.sigmud.model.Pantalla.*;
import pe.gob.sigmud.service.AlmacenService;
import pe.gob.sigmud.util.Validacion;

@WebServlet("/almacen")
public final class AlmacenServlet extends ModuloServlet {
    private static final String BASE = "/almacen";
    private static final String GESTIONAR = "ALMACEN_GESTIONAR";
    private final AlmacenService almacen = new AlmacenService();
    @Override protected ModuloMunicipal modulo() { return ModuloMunicipal.ALMACEN; }

    @Override
    protected Pantalla vista(HttpServletRequest r, Identidad u) throws SQLException {
        Long id = Validacion.idOpcional(dato(r, "id"), "material");
        if (r.getParameter("nuevo") != null || r.getParameter("editar") != null) {
            Validacion.permiso(u, GESTIONAR);
            return formulario(id == null ? null : almacen.detalle(u, id));
        }
        if (id != null) return detalle(r, u, almacen.detalle(u, id));
        String q = dato(r, "q"), alerta = dato(r, "alerta");
        var resultados = almacen.listar(u, q, alerta, Pagina.numero(r.getParameter("pagina")));
        var resumen = almacen.resumen(u);
        Pantalla p = new Pantalla("Almacén y logística", "Controla existencias, lotes y movimientos de inventario.", BASE);
        p.vista("Inventario", BASE, alerta.isEmpty());
        p.vista("Stock bajo", BASE + "?alerta=BAJO", "BAJO".equals(alerta));
        p.vista("Vencimientos", BASE + "?alerta=VENCIMIENTOS", "VENCIMIENTOS".equals(alerta));
        p.estadoVacio("No hay materiales que coincidan con los filtros del inventario.");
        if (u.permisos().contains(GESTIONAR)) p.enlaces().add(new Enlace("Registrar material", BASE + "?nuevo=1"));
        p.filtros(new Formulario("", "Filtrar", "", "", List.of(Campo.texto("q", "Código o nombre del material", q, false, 140),
                Campo.seleccionar("alerta", "Condición del inventario", alerta, false, List.of(new Opcion("BAJO", "Stock bajo"),
                        new Opcion("VENCIMIENTOS", "Lotes vencidos o próximos a vencer"))))));
        p.tablas().add(new Tabla("Resumen de inventario", List.of("Materiales", "En el mínimo o por debajo", "Lotes vencidos o a 30 días"),
                List.of(Fila.texto(Long.toString(resumen.materiales()), Long.toString(resumen.stockBajo()),
                        Long.toString(resumen.lotesPorVencer()))), false));
        p.tablas().add(tabla("Materiales", List.of("Código", "Material", "Unidad", "Existencia", "Stock mínimo", "Estado"),
                resultados.filas().stream().map(m -> Fila.nueva(new Enlace("Ver material", BASE + "?id=" + m.id()), m.codigo(),
                        m.nombre(), m.unidad(), m.stock().toPlainString(), m.minimo().toPlainString(),
                        m.alerta() ? "Stock bajo" : "Disponible")).toList(), true, resultados, BASE, Map.of("q", q, "alerta", alerta)));
        return p;
    }

    private Pantalla detalle(HttpServletRequest r, Identidad u, Material m) throws SQLException {
        String baseDetalle = BASE + "?id=" + m.id();
        boolean gestor = u.permisos().contains(GESTIONAR);
        Pantalla p = new Pantalla(m.nombre(), m.codigo() + " · " + m.stock().toPlainString() + " " + m.unidad(), BASE);
        p.enlaces().add(new Enlace("Volver al inventario", BASE));
        var lotes = almacen.lotes(u, m.id());
        if (gestor) {
            p.enlaces().add(new Enlace("Editar material", baseDetalle + "&editar=1"));
            p.enlaces().add(new Enlace("Registrar lote", baseDetalle + "&lote=nuevo"));
            if (!lotes.isEmpty()) p.enlaces().add(new Enlace("Registrar movimiento", baseDetalle + "&movimiento=1"));
            configurarFormulario(r, u, m, p, lotes);
        }
        String tipo = dato(r, "tipo"), desde = dato(r, "desde"), hasta = dato(r, "hasta");
        if (p.formularios().isEmpty()) {
            p.filtros(new Formulario("Historial de movimientos", "Filtrar historial", "", "", List.of(Campo.oculto("id", m.id()),
                    Campo.seleccionar("tipo", "Tipo de movimiento", tipo, false, opciones(AlmacenService.TIPOS)),
                    Campo.tipo("desde", "Registro desde", "date", desde, false),
                    Campo.tipo("hasta", "Registro hasta", "date", hasta, false))));
        } else {
            p.enlaces().add(new Enlace("Cancelar operación", baseDetalle));
        }
        p.tablas().add(new Tabla("Lotes y existencias", List.of("Lote", "Vencimiento", "Saldo", "Estado"),
                lotes.stream().map(l -> Fila.nueva(gestor ? new Enlace("Editar vencimiento", baseDetalle + "&lote=" + l.id())
                        : null, l.codigo(), l.vencimiento() == null ? null : l.vencimiento().toString(),
                        l.stock().toPlainString(), estadoLote(l))).toList(), gestor));
        var historial = almacen.movimientos(u, m.id(), tipo, desde, hasta, Pagina.numero(r.getParameter("pagina")));
        p.tablas().add(tabla("Historial de movimientos", List.of("Código", "Lote", "Tipo", "Cantidad", "Área", "Motivo", "Registro"),
                historial.filas().stream().map(h -> Fila.texto(h.codigo(), h.lote(), etiqueta(h.tipo()), h.cantidad().toPlainString(),
                        h.area(), h.motivo(), fecha(h.fecha()))).toList(), false, historial, BASE,
                Map.of("id", m.id(), "tipo", tipo, "desde", desde, "hasta", hasta)));
        return p;
    }

    private void configurarFormulario(HttpServletRequest r, Identidad u, Material m, Pantalla p, List<Lote> lotes)
            throws SQLException {
        String lote = dato(r, "lote");
        if (!lote.isEmpty()) {
            Lote actual = "nuevo".equals(lote) ? null : lotes.stream().filter(l -> Long.toString(l.id()).equals(lote))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("El lote no está disponible."));
            p.formularios().add(new Formulario(actual == null ? "Registrar lote" : "Editar vencimiento", "Guardar lote",
                    "lote", "", List.of(Campo.oculto("id", m.id()), Campo.oculto("loteId", actual == null ? "" : actual.id()),
                    actual == null ? Campo.texto("codigo", "Código de lote", "", true, 40) : Campo.oculto("codigo", ""),
                    Campo.tipo("vence", "Fecha de vencimiento", "date", actual == null ? "" : actual.vencimiento(), false))));
        } else if (r.getParameter("movimiento") != null) {
            p.formularios().add(new Formulario("Movimiento de almacén", "Registrar movimiento", "movimiento",
                    "El movimiento se conservará en el historial y actualizará el saldo del lote.", List.of(
                    Campo.oculto("id", m.id()), Campo.seleccionar("loteId", "Lote", "", true, lotes.stream()
                            .map(l -> new Opcion(l.id(), l.codigo() + " · saldo " + l.stock().toPlainString())).toList()),
                    Campo.seleccionar("tipo", "Tipo de movimiento", "", true, opciones(AlmacenService.TIPOS)),
                    Campo.tipo("cantidad", "Cantidad", "number", "", true),
                    Campo.seleccionar("area", "Área solicitante (obligatoria para salidas)", "", false, almacen.areas(u)),
                    Campo.parrafo("motivo", "Motivo", "", true, 250))));
        }
    }

    private static Pantalla formulario(Material m) {
        Pantalla p = new Pantalla(m == null ? "Registrar material" : "Editar material",
                m == null ? "Después de guardar el material, registra un lote y su primer ingreso." : m.codigo() + " · " + m.unidad(), BASE);
        p.enlaces().add(new Enlace("Cancelar", m == null ? BASE : BASE + "?id=" + m.id()));
        p.formularios().add(new Formulario("Datos del material", "Guardar material", "material", "", List.of(
                Campo.oculto("id", m == null ? "" : m.id()),
                m == null ? Campo.texto("codigo", "Código", "", true, 40) : Campo.oculto("codigo", ""),
                Campo.texto("nombre", "Nombre", m == null ? "" : m.nombre(), true, 140),
                m == null ? Campo.texto("unidad", "Unidad de medida", "", true, 35) : Campo.oculto("unidad", ""),
                Campo.tipo("minimo", "Stock mínimo", "number", m == null ? "0" : m.minimo().toPlainString(), true))));
        return p;
    }

    @Override
    protected Cambio operar(HttpServletRequest r, Identidad u) throws SQLException {
        Long id = Validacion.idOpcional(dato(r, "id"), "material");
        return switch (dato(r, "op")) {
            case "material" -> {
                long guardado = almacen.guardarMaterial(u, id, dato(r, "codigo"), dato(r, "nombre"), dato(r, "unidad"),
                        dato(r, "minimo"));
                yield new Cambio(BASE + "?id=" + guardado, "Se guardó el material.");
            }
            case "lote" -> {
                almacen.guardarLote(u, Validacion.id(dato(r, "id"), "material"), Validacion.idOpcional(dato(r, "loteId"), "lote"),
                        dato(r, "codigo"), dato(r, "vence"));
                yield new Cambio(BASE + "?id=" + id, "Se guardó el lote.");
            }
            case "movimiento" -> {
                almacen.movimiento(u, Validacion.id(dato(r, "id"), "material"), Validacion.id(dato(r, "loteId"), "lote"),
                        dato(r, "tipo"), dato(r, "cantidad"), Validacion.idOpcional(dato(r, "area"), "área"), dato(r, "motivo"));
                yield new Cambio(BASE + "?id=" + id, "Se registró el movimiento.");
            }
            default -> throw new IllegalArgumentException("Operación no disponible.");
        };
    }

    private static String estadoLote(Lote l) {
        if (l.stock().signum() == 0) return "Sin existencias";
        if (l.vencimiento() == null) return "Sin vencimiento";
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));
        if (l.vencimiento().isBefore(hoy)) return "Vencido";
        return !l.vencimiento().isAfter(hoy.plusDays(30)) ? "Por vencer" : "Vigente";
    }

    @Override
    protected String volverTrasError(HttpServletRequest r) {
        String id = dato(r, "id");
        if (!id.matches("[0-9]{1,18}")) return BASE + "?nuevo=1";
        return BASE + "?id=" + id + switch (dato(r, "op")) {
            case "material" -> "&editar=1";
            case "movimiento" -> "&movimiento=1";
            default -> "&lote=" + (dato(r, "loteId").matches("[0-9]{1,18}") ? dato(r, "loteId") : "nuevo");
        };
    }
}
