package pe.gob.sigmud.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import pe.gob.sigmud.dao.impl.PanelJdbcDao;
import pe.gob.sigmud.dao.impl.PanelPerfilJdbcDao;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.ModuloMunicipal;
import pe.gob.sigmud.model.PanelMunicipal;
import pe.gob.sigmud.model.PanelMunicipal.Indicador;
import pe.gob.sigmud.model.Pantalla.Enlace;
import pe.gob.sigmud.model.Pantalla.Fila;
import pe.gob.sigmud.model.Pantalla.Tabla;
import pe.gob.sigmud.model.VistaInicio;
import pe.gob.sigmud.model.VistaInicio.Acceso;
import pe.gob.sigmud.model.VistaInicio.Bloque;
import pe.gob.sigmud.model.VistaInicio.Buscador;
import pe.gob.sigmud.model.VistaInicio.Kpi;
import pe.gob.sigmud.model.VistaInicio.Perfil;

import static pe.gob.sigmud.model.ModuloMunicipal.*;

public final class PanelService {

    private final PanelJdbcDao dao;
    private final PanelPerfilJdbcDao perfiles = new PanelPerfilJdbcDao();

    public PanelService() {
        this(new PanelJdbcDao());
    }

    PanelService(PanelJdbcDao dao) {
        this.dao = dao;
    }

    public PanelMunicipal consultar(Identidad identidad) throws SQLException {
        List<Indicador> indicadores = Arrays.stream(Indicador.values())
                .filter(indicador -> indicador.modulo().permitido(identidad))
                .toList();
        return dao.consultar(identidad, indicadores);
    }

    public VistaInicio vista(Identidad identidad) throws SQLException {
        Perfil perfil = Perfil.de(identidad);
        var contenido = perfiles.consultar(identidad, perfil);
        List<Bloque> bloques = new ArrayList<>(contenido.bloques());
        List<Kpi> kpis = new ArrayList<>(contenido.indicadores());
        if (perfil == Perfil.ADMINISTRADOR || perfil == Perfil.GENERAL) {
            integrarResumen(consultar(identidad), perfil, kpis, bloques);
        }
        return new VistaInicio(perfil, kpis, bloques,
                accesos(perfil).stream().filter(a -> a.modulo().permitido(identidad)
                        && (a.permiso() == null || identidad.permisos().contains(a.permiso())))
                        .map(Accion::acceso).toList(), buscador(perfil, identidad));
    }

    private static void integrarResumen(PanelMunicipal general, Perfil perfil, List<Kpi> kpis, List<Bloque> bloques) {
        if (perfil == Perfil.GENERAL) {
            for (var m : general.mediciones()) {
                var i = m.indicador();
                kpis.add(new Kpi(i.titulo(), m.total(), i.descripcion(), i.modulo().icono(), i.ruta(), false));
            }
        }
        if (!general.mediciones().isEmpty()) {
            if (perfil == Perfil.ADMINISTRADOR) {
                var resumen = general.mediciones().stream()
                        .map(m -> Fila.nueva(new Enlace("Consultar", m.indicador().ruta()),
                                m.indicador().titulo(), Long.toString(m.total())))
                        .toList();
                bloques.add(0, new Bloque(new Tabla("Resumen de operaciones", List.of("Indicador", "Registros"),
                        resumen, true), "Carga de trabajo y existencias en los módulos autorizados",
                        "No hay indicadores disponibles.", "chart", null));
            }
            var recientes = general.actividad().stream()
                    .map(a -> Fila.nueva(new Enlace("Ir al módulo", a.modulo().ruta()),
                            a.referencia(), a.evento(), a.fechaVisible()))
                    .toList();
            bloques.add(new Bloque(new Tabla("Actividad reciente", List.of("Referencia", "Operación", "Fecha"),
                    recientes, true), "Últimos movimientos con fecha transcurrida",
                    "No hay movimientos recientes en los módulos autorizados.", "clock", null));
            if (!general.proximasCitas().isEmpty()) {
                var agenda = general.proximasCitas().stream()
                        .map(c -> Fila.texto(c.referencia(), c.ventanilla(), c.fechaVisible())).toList();
                bloques.add(new Bloque(new Tabla("Próximas citas", List.of("Cita", "Ventanilla", "Inicio"),
                        agenda, false), "Agenda municipal", "No hay citas próximas.", "calendar", "/citas?estado=PROGRAMADA"));
            }
        }
    }

    private static Buscador buscador(Perfil perfil, Identidad identidad) {
        if (!CONTRIBUYENTES.permitido(identidad)) return null;
        return switch (perfil) {
            case RENTAS -> new Buscador(BUSQUEDAS.ruta(), "PREDIOS", "Buscar por código de predio", "Código de predio");
            case REGISTRO_CIVIL -> new Buscador(CONTRIBUYENTES.ruta(), "NATURAL", "Buscar una persona por documento", "DNI o RUC");
            default -> null;
        };
    }

    private record Accion(ModuloMunicipal modulo, String permiso, Acceso acceso) {}

    private static Accion accion(ModuloMunicipal modulo, String permiso, String titulo, String descripcion, String consulta) {
        return new Accion(modulo, permiso, new Acceso(titulo, descripcion, modulo.icono(), modulo.ruta() + consulta));
    }

    private static List<Accion> accesos(Perfil p) {
        return switch (p) {
            case ADMINISTRADOR -> List.of(
                    accion(SEGURIDAD, null, "Administrar usuarios", "Cuentas, roles y estado de acceso", ""),
                    accion(SEGURIDAD, null, "Consultar permisos", "Matriz vigente por rol", "?permisos=1"),
                    accion(REPORTES, null, "Abrir reportes", "Indicadores de los módulos autorizados", ""));
            case MESA_PARTES -> List.of(
                    accion(TRAMITES, "TRAMITES_REGISTRAR", "Registrar expediente", "Nuevo ingreso documentario", "?nuevo=1"),
                    accion(TRAMITES, null, "Consultar expedientes", "Bandeja y seguimiento", ""),
                    accion(CONTRIBUYENTES, "CONTRIBUYENTES_GESTIONAR", "Registrar contribuyente", "Alta en el padrón", "?nuevo=1"),
                    accion(REPORTES, null, "Trámites por estado", "Resumen de expedientes", "?reporte=TRAMITES_ESTADO"));
            case RENTAS -> List.of(
                    accion(CONTRIBUYENTES, null, "Consultar padrón", "Personas naturales y jurídicas", ""),
                    accion(BUSQUEDAS, "CONTRIBUYENTES_CONSULTAR", "Buscar documentos", "Consulta por DNI o RUC", "?tipo=DOCUMENTOS"),
                    accion(TRAMITES, null, "Trámites de Rentas", "Seguimiento del área", ""));
            case FISCALIZACION_DEFENSA_CIVIL -> List.of(
                    accion(RECLAMOS, "RECLAMOS_REGISTRAR", "Registrar caso", "Reclamo o emergencia", "?nuevo=1"),
                    accion(RECLAMOS, null, "Abrir cola de atención", "Casos pendientes y en atención", ""),
                    accion(REPORTES, "RECLAMOS_CONSULTAR", "Reporte de casos", "Áreas, prioridades y estados", "?reporte=RECLAMOS"));
            case ATENCION_VECINO -> List.of(
                    accion(RECLAMOS, "RECLAMOS_REGISTRAR", "Registrar solicitud", "Reclamos y emergencias municipales", "?nuevo=1"),
                    accion(RECLAMOS, null, "Consultar casos atendidos", "Historial del área", "?estado=ATENDIDO"),
                    accion(CITAS, null, "Consultar agenda del vecino", "Citas del área y sus estados", ""));
            case MERCADOS -> List.of(
                    accion(MERCADOS, null, "Consultar puestos libres", "Disponibilidad para asignar", "?estado=LIBRE"),
                    accion(MERCADOS, "MERCADOS_GESTIONAR", "Registrar puesto", "Seleccionar mercado y sector", "?nuevo=1"),
                    accion(MERCADOS, null, "Revisar sectores", "Catálogo de sectores", "?catalogo=SECTORES"),
                    accion(MERCADOS, null, "Revisar mercados", "Catálogo de mercados", "?catalogo=MERCADOS"));
            case ALMACEN -> List.of(
                    accion(ALMACEN, null, "Abrir inventario", "Seleccionar un material para registrar movimientos", ""),
                    accion(ALMACEN, "ALMACEN_GESTIONAR", "Registrar material", "Nueva referencia de inventario", "?nuevo=1"),
                    accion(ALMACEN, null, "Consultar vencimientos", "Lotes con saldo que requieren revisión", "?alerta=VENCIMIENTOS"),
                    accion(REPORTES, "ALMACEN_CONSULTAR", "Reporte de movimientos", "Ingresos y salidas", "?reporte=MOVIMIENTOS"));
            case REGISTRO_CIVIL -> List.of(
                    accion(CONTRIBUYENTES, null, "Abrir fichas personales", "Personas naturales registradas", "?tipo=NATURAL"),
                    accion(CITAS, null, "Consultar agenda de Registro Civil", "Horarios y citas programadas", ""),
                    accion(TRAMITES, null, "Consultar expedientes atendidos", "Historial del área", "?estado=ATENDIDO"));
            case GENERAL -> Arrays.stream(ModuloMunicipal.values())
                    .map(m -> accion(m, null, m.titulo(), "", "")).toList();
        };
    }
}
