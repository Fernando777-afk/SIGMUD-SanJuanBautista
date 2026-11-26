package pe.gob.sigmud.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import org.h2.jdbcx.JdbcDataSource;
import pe.gob.sigmud.dao.impl.PanelJdbcDao;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.ModuloMunicipal;
import pe.gob.sigmud.model.PanelMunicipal;

public final class ComprobarPanelJdbc {

    private ComprobarPanelJdbc() {}

    public static void main(String[] args) throws SQLException {
        JdbcDataSource fuente = new JdbcDataSource();
        fuente.setURL("jdbc:h2:mem:panel;MODE=MySQL");
        try (Connection conexion = fuente.getConnection(); Statement sql = conexion.createStatement()) {
            preparar(sql);
            PanelService panel = new PanelService(new PanelJdbcDao(fuente));
            comprobarAdministrador(panel);
            comprobarAreas(panel);
            comprobarModuloUnico(panel);
            comprobarSinPermisos(panel);
        }
    }

    private static void comprobarAdministrador(PanelService servicio) throws SQLException {
        PanelMunicipal panel = servicio.consultar(identidad(1, "ADMINISTRADOR",
                "CONTRIBUYENTES_CONSULTAR", "TRAMITES_CONSULTAR", "RECLAMOS_CONSULTAR",
                "CITAS_CONSULTAR", "MERCADOS_CONSULTAR", "ALMACEN_CONSULTAR"));
        verificar(List.of(1L, 1L, 4L, 1L, 1L, 2L).equals(totales(panel)),
                "Los seis indicadores respetan estados, fechas, bajas y mínimos");
        verificar(panel.actividad().size() == 5
                && "MOV-1".equals(panel.actividad().get(0).referencia()), "Actividad ordenada y limitada a cinco");
        verificar(panel.proximasCitas().size() == 1 && "C-2".equals(panel.proximasCitas().get(0).referencia()),
                "La agenda muestra solo citas futuras programadas");
    }

    private static void comprobarAreas(PanelService servicio) throws SQLException {
        PanelMunicipal atencion = servicio.consultar(identidad(2, "ATENCION_VECINO",
                "RECLAMOS_CONSULTAR", "CITAS_CONSULTAR"));
        verificar(totales(atencion).equals(List.of(1L, 0L)) && atencion.proximasCitas().isEmpty()
                && atencion.actividad().stream().map(PanelMunicipal.Actividad::referencia).toList().equals(List.of("C-1", "AT-1")),
                "Atención al Vecino: solo su área y cero real");
        PanelMunicipal defensa = servicio.consultar(identidad(3, "FISCALIZACION_DEFENSA_CIVIL", "RECLAMOS_CONSULTAR"));
        verificar(totales(defensa).equals(List.of(2L)) && defensa.actividad().size() == 3,
                "Fiscalización y Defensa Civil: las dos áreas autorizadas");
        PanelMunicipal sinArea = servicio.consultar(identidad(4, "MESA_PARTES", "TRAMITES_CONSULTAR"));
        verificar(totales(sinArea).equals(List.of(0L)) && sinArea.actividad().isEmpty(),
                "Una cuenta sin área asignada carece de alcance sobre trámites");
    }

    private static void comprobarModuloUnico(PanelService servicio) throws SQLException {
        PanelMunicipal panel = servicio.consultar(identidad(1, "ALMACEN", "ALMACEN_CONSULTAR"));
        verificar(totales(panel).equals(List.of(2L)) && panel.actividad().size() == 1
                && panel.actividad().get(0).modulo() == ModuloMunicipal.ALMACEN,
                "Almacén: únicamente el indicador y la actividad de su módulo");
    }

    private static void comprobarSinPermisos(PanelService servicio) throws SQLException {
        PanelMunicipal panel = servicio.consultar(identidad(1, "ADMINISTRADOR", "REPORTES_CONSULTAR"));
        verificar(panel.mediciones().isEmpty() && panel.actividad().isEmpty(),
                "El permiso de reportes por sí solo no expone otros módulos");
    }

    private static List<Long> totales(PanelMunicipal panel) {
        return panel.mediciones().stream().map(PanelMunicipal.Medicion::total).toList();
    }

    private static Identidad identidad(long id, String rol, String... permisos) {
        return new Identidad(id, "prueba", "Cuenta de prueba", 1, rol, rol, Set.of(permisos));
    }

    private static void verificar(boolean condicion, String caso) {
        if (!condicion) {
            throw new AssertionError(caso);
        }
        System.out.println("OK " + caso);
    }

    private static void preparar(Statement sql) throws SQLException {
        String datos = """
                CREATE TABLE areas (id INT PRIMARY KEY, codigo VARCHAR(50), baja_en TIMESTAMP);
                CREATE TABLE usuarios (id INT PRIMARY KEY, area_id INT);
                CREATE TABLE contribuyentes (id INT PRIMARY KEY, baja_en TIMESTAMP);
                CREATE TABLE tramites (id INT PRIMARY KEY, numero_expediente VARCHAR(40));
                CREATE TABLE historial_tramites (id INT PRIMARY KEY, tramite_id INT, area_id INT,
                    estado VARCHAR(30), registrado_en TIMESTAMP);
                CREATE TABLE reclamos_emergencias (codigo_caso VARCHAR(40), area_id INT,
                    estado VARCHAR(30), registrado_en TIMESTAMP);
                CREATE TABLE ventanillas (id INT PRIMARY KEY, area_id INT, nombre VARCHAR(100));
                CREATE TABLE citas (codigo_cita VARCHAR(40), ventanilla_id INT, estado VARCHAR(30),
                    inicio TIMESTAMP, registrado_en TIMESTAMP);
                CREATE TABLE mercados (id INT PRIMARY KEY, codigo VARCHAR(40), baja_en TIMESTAMP);
                CREATE TABLE puestos (id INT PRIMARY KEY, mercado_id INT, numero VARCHAR(40), baja_en TIMESTAMP);
                CREATE TABLE asignaciones_puestos (puesto_id INT, asignado_en TIMESTAMP, liberado_en TIMESTAMP);
                CREATE TABLE materiales (id INT PRIMARY KEY, stock_minimo DECIMAL(14,3), baja_en TIMESTAMP);
                CREATE TABLE lotes_material (material_id INT, stock DECIMAL(14,3));
                CREATE TABLE movimientos_stock (codigo_movimiento VARCHAR(40), registrado_en TIMESTAMP);
                INSERT INTO areas VALUES (1,'ATENCION_VECINO',NULL), (2,'FISCALIZACION',NULL),
                    (3,'DEFENSA_CIVIL',NULL), (4,'RENTAS',NULL);
                INSERT INTO usuarios VALUES (1,NULL), (2,1), (3,2), (4,NULL);
                INSERT INTO contribuyentes VALUES (1,NULL), (2,'2025-01-01');
                INSERT INTO tramites VALUES (1,'T-1'), (2,'T-2');
                INSERT INTO historial_tramites VALUES (1,1,1,'RECIBIDO','2025-01-01'),
                    (2,1,4,'ARCHIVADO','2025-01-02'), (3,2,4,'EN_TRAMITE','2025-01-03');
                INSERT INTO reclamos_emergencias VALUES ('AT-1',1,'PENDIENTE','2025-01-05'),
                    ('FI-1',2,'EN_ATENCION','2025-01-06'), ('DC-1',3,'PENDIENTE','2025-01-07'),
                    ('RT-1',4,'PENDIENTE','2025-01-08'), ('DC-2',3,'ATENDIDO','2025-01-09');
                INSERT INTO ventanillas VALUES (1,1,'Ventanilla A'), (2,2,'Ventanilla B');
                INSERT INTO citas VALUES ('C-1',1,'PROGRAMADA','2000-01-01','2025-01-10'),
                    ('C-2',2,'PROGRAMADA','2099-01-01','2025-01-11'),
                    ('C-3',2,'CANCELADA','2099-01-02','2025-01-12');
                INSERT INTO mercados VALUES (1,'M-01',NULL), (2,'M-02','2025-01-01');
                INSERT INTO puestos VALUES (1,1,'01',NULL), (2,2,'02',NULL);
                INSERT INTO asignaciones_puestos VALUES (1,'2025-01-13',NULL), (2,'2025-01-13',NULL);
                INSERT INTO materiales VALUES (1,10,NULL), (2,0,NULL), (3,5,NULL), (4,5,'2025-01-01');
                INSERT INTO lotes_material VALUES (1,7), (1,3), (3,6);
                INSERT INTO movimientos_stock VALUES ('MOV-1','2025-01-15'), ('MOV-FUTURA','2099-01-01');
                """;
        for (String sentencia : datos.split(";")) {
            if (!sentencia.isBlank()) {
                sql.execute(sentencia);
            }
        }
    }
}
