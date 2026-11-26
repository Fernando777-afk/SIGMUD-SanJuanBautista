package pe.gob.sigmud.service;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import pe.gob.sigmud.dao.Jdbc;
import pe.gob.sigmud.dao.impl.MercadoJdbcDao.Catalogo;
import pe.gob.sigmud.dao.impl.BusquedaJdbcDao.Orden;
import pe.gob.sigmud.dao.impl.BusquedaJdbcDao.Tipo;
import pe.gob.sigmud.dao.impl.ReportesJdbcDao.Reporte;
import pe.gob.sigmud.dao.impl.UsuarioJdbcDao;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.Tramite;
import pe.gob.sigmud.util.Contrasenas;

public final class ComprobarGestionMySql {
    private final UsuariosService usuarios = new UsuariosService();
    private final ContribuyentesService contribuyentes = new ContribuyentesService();
    private final TramitesService tramites = new TramitesService();
    private final CasosService casos = new CasosService();
    private final CitasService citas = new CitasService();
    private final MercadosService mercados = new MercadosService();
    private final AlmacenService almacen = new AlmacenService();

    private ComprobarGestionMySql() {}

    public static void main(String[] args) throws Exception {
        if (!"jdbc:mysql://127.0.0.1:13306/sigmud_db".equals(System.getenv("SIGMUD_DB_URL"))) {
            throw new IllegalStateException("Ejecuta la comprobación únicamente en la instancia aislada del puerto 13306.");
        }
        new ComprobarGestionMySql().ejecutar();
    }

    private void ejecutar() throws Exception {
        String version = Jdbc.leer(c -> Jdbc.listar(c, "SELECT VERSION()", f -> f.getString(1)).get(0));
        System.out.println("MySQL de integración " + version);
        String claveLocal = System.getenv("SIGMUD_QA_CLAVE");
        char[] clave = (claveLocal == null ? UUID.randomUUID().toString() : claveLocal).toCharArray();
        try {
            var dao = new UsuarioJdbcDao();
            String hash = Contrasenas.generar(clave);
            for (String cuenta : java.util.List.of("demo.admin", "demo.mesa", "demo.rentas", "demo.defensa",
                    "demo.atencion", "demo.mercados", "demo.almacen", "demo.registro")) {
                exigir(dao.inicializarDemo(cuenta, hash), "Inicialización de cuenta en MySQL aislado: " + cuenta);
            }
            var servicio = new AutenticacionService();
            var sesion = servicio.autenticar("demo.admin", clave).orElseThrow();
            var admin = servicio.consultarSesion(sesion).orElseThrow();
            var rentas = servicio.consultarSesion(servicio.autenticar("demo.rentas", clave).orElseThrow()).orElseThrow();
            exigir("Administración".equals(admin.nombreVisible()), "Login y normalización visible");
            long area = idArea("RENTAS");
            long defensa = idArea("DEFENSA_CIVIL");
            probarUsuarios(admin, area, clave);
            long persona = probarContribuyentes(admin, rentas);
            probarTramites(admin, rentas, persona, area, defensa);
            probarCasos(admin, rentas, persona, area, defensa);
            probarCitas(admin, rentas, persona);
            probarMercados(admin, persona);
            probarAlmacen(admin, area);
            probarConsultas(admin, rentas);
            exigir(new PanelService().consultar(admin).mediciones().size() == 6, "Dashboard MySQL");
        } finally {
            Arrays.fill(clave, '\0');
        }
    }

    private void probarUsuarios(Identidad admin, long area, char[] clave) throws SQLException {
        int rol = usuarios.roles().stream().filter(r -> r.nombre().equals("Rentas")).findFirst().orElseThrow().id();
        usuarios.guardar(admin, 0, "integracion.operador", "Integración de acceso", rol,
                true, clave, false, (int) area);
        var creada = usuarios.listar("integracion.operador", rol, "activa", 1).filas().get(0);
        usuarios.guardar(admin, creada.id(), "integracion.renombrada", "Integración editada", rol,
                false, new char[0], false, (int) area);
        exigir(usuarios.listar("integracion.renombrada", rol, "inactiva", 1).filas().size() == 1
                && usuarios.buscar(creada.id()).orElseThrow().areaId() == area
                && !usuarios.permisos().isEmpty(), "Usuarios, áreas, filtros y permisos");
        esperarRechazo(() -> usuarios.guardar(admin, admin.id(), "otro.admin", "Administración",
                admin.rolId(), true, new char[0], false, null));
        esperarRechazo(() -> usuarios.guardar(admin, admin.id(), "demo.admin", "Administración",
                admin.rolId(), false, new char[0], false, null));
    }

    private long probarContribuyentes(Identidad admin, Identidad rentas) throws SQLException {
        long id = contribuyentes.guardar(admin, 0, "NATURAL", "99001234", "", "Persona de integración", "+51900000000",
                "", "1990-01-01");
        contribuyentes.guardar(admin, id, "NATURAL", "99001234", "", "Persona editada", "", "", "1990-01-02");
        contribuyentes.vincular(admin, id, "INT-P-001");
        exigir(contribuyentes.listar(admin, "INT-P-001", "", "activos", 1).filas().get(0).id() == id,
                "Alta, actualización, predio y búsqueda de contribuyente");
        esperarRechazo(() -> contribuyentes.guardar(admin, 0, "NATURAL", "99001234", "", "Duplicado", "", "", ""));
        long baja = contribuyentes.guardar(admin, 0, "JURIDICA", "", "00000000025", "Entidad de integración", "", "", "");
        contribuyentes.baja(admin, baja);
        exigir(!contribuyentes.consultar(admin, baja).activo(), "Baja lógica de persona jurídica");
        try {
            contribuyentes.baja(rentas, id);
            throw new AssertionError("El rol de consulta dio de baja un registro");
        } catch (SecurityException esperada) {
            System.out.println("OK Restricción de escritura por permiso");
        }
        return id;
    }

    private void probarTramites(Identidad admin, Identidad rentas, long persona, long area, long destino) throws SQLException {
        String codigo = tramites.registrar(admin, persona, area, "Trámite de integración", "Control de trazabilidad");
        Tramite t = tramites.listar(rentas, codigo, persona, area, "RECIBIDO", "", "", 1).filas().get(0);
        tramites.actualizar(rentas, t.id(), t.version(), destino, "EN_TRAMITE", "Derivación de integración");
        esperarRechazo(() -> tramites.consultar(rentas, t.id()));
        exigir(tramites.historial(admin, persona).get(0).movimientos().size() == 2,
                "Registro, derivación, historial y alcance de expedientes");
    }

    private void probarCasos(Identidad admin, Identidad rentas, long persona, long area, long defensa) throws SQLException {
        long id = casos.registrar(admin, "EMERGENCIA", "ALTA", defensa, persona, "Caso de integración", "Referencia ficticia");
        esperarRechazo(() -> casos.detalle(rentasConConsulta(rentas), id));
        casos.atender(admin, id, area, "ATENDIDO", "CRITICA", defensa, "PENDIENTE", "ALTA");
        exigir(casos.detalle(admin, id).atencion() != null && casos.listar(admin, "", "EMERGENCIA", "CRITICA", "ATENDIDO",
                area, "", "", 1).filas().stream().anyMatch(c -> c.id() == id), "Registro, atención y filtros de casos");
    }

    private void probarCitas(Identidad admin, Identidad rentas, long persona) throws Exception {
        long v = Long.parseLong(citas.ventanillas(rentas).get(0).valor());
        long f = Long.parseLong(citas.funcionarios(rentas, v).get(0).valor());
        long original = citas.guardar(admin, null, persona, v, f, "2099-06-01T09:00", "2099-06-01T09:30", "Integración");
        esperarRechazo(() -> citas.guardar(admin, null, persona, v, f, "2099-06-01T09:15", "2099-06-01T09:45", "Cruce"));
        long otraVentanilla = Long.parseLong(citas.ventanillas(rentas).get(1).valor());
        exigir(!citas.disponible(admin, otraVentanilla, f, "2099-06-01T09:15", "2099-06-01T09:45"),
                "Disponibilidad verifica al funcionario entre ventanillas");
        esperarRechazo(() -> citas.guardar(admin, null, persona, otraVentanilla, f,
                "2099-06-01T09:15", "2099-06-01T09:45", "Cruce de funcionario"));
        Jdbc.leer(c -> Jdbc.actualizar(c, "INSERT INTO ventanillas_funcionarios (ventanilla_id, funcionario_id) VALUES (?, ?)", v, admin.id()));
        exigir(!citas.disponible(admin, v, admin.id(), "2099-06-01T09:15", "2099-06-01T09:45"),
                "Disponibilidad verifica la ventanilla entre funcionarios");
        esperarRechazo(() -> citas.guardar(admin, null, persona, v, admin.id(),
                "2099-06-01T09:15", "2099-06-01T09:45", "Cruce de ventanilla"));
        exigir(true, "Solapamientos independientes por ventanilla y funcionario");
        long consecutiva = citas.guardar(admin, null, persona, v, f, "2099-06-01T09:30", "2099-06-01T10:00", "Consecutiva");
        esperarRechazo(() -> citas.guardar(admin, original, 0, v, f, "2099-06-01T09:45", "2099-06-01T10:15", "Cruce"));
        exigir(citas.detalle(admin, original).programada(), "Reprogramación rechazada revierte la cancelación");
        long reemplazo = citas.guardar(admin, original, 0, v, f, "2099-06-01T10:00", "2099-06-01T10:30", "Reprogramada");
        citas.finalizar(admin, consecutiva, "CANCELADA");
        exigir(citas.detalle(admin, reemplazo).programada() && "CANCELADA".equals(citas.detalle(admin, original).estado())
                && citas.disponible(admin, v, f, "2099-06-01T09:00", "2099-06-01T10:00"), "Agenda, reemplazo y cancelación de citas");
        comprobarCarrera(() -> citas.guardar(admin, null, persona, v, f, "2099-06-02T09:00", "2099-06-02T09:30", "Paralelo"),
                "Una sola reserva cuando dos solicitudes concurren");
    }

    private void probarMercados(Identidad admin, long persona) throws Exception {
        mercados.guardarCatalogo(admin, Catalogo.MERCADOS, null, "INT-MER", "Mercado de integración");
        mercados.guardarCatalogo(admin, Catalogo.SECTORES, null, "INT-SEC", "Sector de integración");
        long mercado = mercados.catalogo(admin, Catalogo.MERCADOS).stream().filter(e -> "INT-MER".equals(e.codigo())).findFirst().orElseThrow().id();
        long sector = mercados.catalogo(admin, Catalogo.SECTORES).stream().filter(e -> "INT-SEC".equals(e.codigo())).findFirst().orElseThrow().id();
        long puesto = mercados.altaPuesto(admin, mercado, sector, "INT-01");
        comprobarCarrera(() -> { mercados.asignar(admin, puesto, persona, "Giro de integración"); return null; },
                "Una sola asignación vigente en concurrencia");
        mercados.liberar(admin, puesto, mercados.detalle(admin, puesto).asignacion());
        exigir(!mercados.detalle(admin, puesto).ocupado() && mercados.historial(admin, puesto, 1).filas().size() == 1,
                "Liberación del puesto conserva el historial");
    }

    private void probarAlmacen(Identidad admin, long area) throws Exception {
        long material = almacen.guardarMaterial(admin, null, "INT-MAT", "Insumo de integración", "unidad", "4");
        almacen.guardarMaterial(admin, material, "", "Insumo actualizado", "", "4");
        almacen.guardarLote(admin, material, null, "INT-LOTE", "2030-03-01");
        long lote = almacen.lotes(admin, material).get(0).id();
        almacen.movimiento(admin, material, lote, "INGRESO", "10", null, "Ingreso de integración");
        comprobarCarrera(() -> { almacen.movimiento(admin, material, lote, "SALIDA", "7", area, "Salida paralela"); return null; },
                "Dos salidas paralelas no generan saldo negativo");
        exigir(almacen.detalle(admin, material).stock().intValueExact() == 3
                && almacen.movimientos(admin, material, "", "", "", 1).filas().size() == 2
                && almacen.resumen(admin).stockBajo() > 0, "Inventario, trazabilidad y alertas");
    }

    private static Identidad rentasConConsulta(Identidad r) {
        return new Identidad(r.id(), r.usuario(), r.nombreVisible(), r.rolId(), r.codigoRol(), r.nombreRol(),
                java.util.Set.of("RECLAMOS_CONSULTAR"));
    }

    private void probarConsultas(Identidad admin, Identidad rentas) throws SQLException {
        var reportes = new ReportesService();
        exigir(reportes.autorizados(admin).size() == 8, "Catálogo de ocho reportes");
        for (Reporte r : reportes.autorizados(admin)) {
            var filtros = reportes.filtros(r, "", "", "", "");
            exigir(!reportes.listar(admin, r, filtros, 1).filas().isEmpty()
                    && !reportes.exportar(admin, r, filtros).isEmpty(), "Reporte y datos para CSV " + r.name());
        }
        var busqueda = new BusquedasService();
        var filas = busqueda.buscar(admin, null, "INT", null, Orden.TIPO, 1).filas();
        exigir(filas.stream().anyMatch(r -> r.tipo() == Tipo.PREDIOS)
                && filas.stream().anyMatch(r -> r.tipo() == Tipo.MATERIALES), "Búsqueda multiárea y enlaces a fichas");
        exigir(busqueda.buscar(rentas, Tipo.EXPEDIENTES, "Trámite de integración", null, Orden.TITULO, 1)
                .filas().isEmpty(), "Búsqueda excluye expedientes derivados a otra área");
        try {
            reportes.exportar(rentas, Reporte.STOCK, reportes.filtros(Reporte.STOCK, "", "", "", ""));
            throw new AssertionError("El rol de Rentas exportó datos de almacén");
        } catch (SecurityException esperada) {
            System.out.println("OK Reportes respetan los permisos del módulo de origen");
        }
    }

    private static void comprobarCarrera(Callable<?> operacion, String caso) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var salida = new CountDownLatch(1);
        Callable<Boolean> intenta = () -> {
            salida.await();
            try { operacion.call(); return true; } catch (IllegalArgumentException esperada) { return false; }
        };
        try {
            var uno = pool.submit(intenta);
            var dos = pool.submit(intenta);
            salida.countDown();
            exigir(uno.get() != dos.get(), caso);
        } finally {
            pool.shutdownNow();
        }
    }

    private static long idArea(String codigo) throws SQLException {
        return Jdbc.leer(c -> Jdbc.listar(c, "SELECT id FROM areas WHERE codigo = ?", f -> f.getLong(1), codigo).get(0));
    }

    @FunctionalInterface
    private interface Operacion { void ejecutar() throws SQLException; }

    private static void esperarRechazo(Operacion operacion) throws SQLException {
        try { operacion.ejecutar(); } catch (IllegalArgumentException esperada) { return; }
        throw new AssertionError("La operación inválida fue aceptada.");
    }

    private static void exigir(boolean condicion, String caso) {
        if (!condicion) throw new AssertionError(caso);
        System.out.println("OK " + caso);
    }
}
