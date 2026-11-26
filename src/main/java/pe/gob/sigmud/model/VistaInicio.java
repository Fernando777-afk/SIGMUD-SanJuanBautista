package pe.gob.sigmud.model;

import java.util.List;
import pe.gob.sigmud.model.Pantalla.Tabla;

public record VistaInicio(Perfil perfil, List<Kpi> indicadores, List<Bloque> bloques,
        List<Acceso> accesos, Buscador buscador) {

    public VistaInicio {
        indicadores = List.copyOf(indicadores);
        bloques = List.copyOf(bloques);
        accesos = List.copyOf(accesos);
    }

    public record Kpi(String titulo, long valor, String descripcion, String icono, String ruta, boolean alerta) {}
    public record Bloque(Tabla tabla, String descripcion, String vacio, String icono, String ruta) {}
    public record Acceso(String titulo, String descripcion, String icono, String ruta) {}
    public record Buscador(String ruta, String tipo, String etiqueta, String ejemplo) {}

    public enum Perfil {
        ADMINISTRADOR("Supervisión municipal", "Administración del sistema",
                "Supervisa las cuentas, la actividad y el estado de los servicios municipales.", "shield"),
        MESA_PARTES("Mesa de Partes", "Gestión documentaria",
                "Revisa los expedientes de tu área y las derivaciones que registraste.", "file"),
        RENTAS("Consulta tributaria", "Rentas",
                "Consulta contribuyentes vigentes, predios vinculados y expedientes de tu área.", "search"),
        FISCALIZACION_DEFENSA_CIVIL("Fiscalización y Defensa Civil", "Operaciones y respuesta",
                "Identifica los casos abiertos y organiza la atención por prioridad.", "alert"),
        ATENCION_VECINO("Atención al Vecino", "Servicios al ciudadano",
                "Da seguimiento a las solicitudes recibidas y a las próximas citas del área.", "people"),
        MERCADOS("Control de mercados", "Puestos y asignaciones",
                "Revisa la disponibilidad por mercado y sector, y consulta las asignaciones vigentes.", "store"),
        ALMACEN("Inventario y logística", "Existencias y movimientos",
                "Controla el stock mínimo, los lotes próximos a vencer y los movimientos de almacén.", "box"),
        REGISTRO_CIVIL("Consulta de personas", "Registro Civil",
                "Localiza fichas de personas por documento y revisa la agenda y los trámites de tu área.", "people"),
        GENERAL("Vista municipal", "Gestión interna",
                "Consulta las funciones disponibles para tu perfil.", "home");

        private final String titulo;
        private final String ambito;
        private final String descripcion;
        private final String icono;

        Perfil(String titulo, String ambito, String descripcion, String icono) {
            this.titulo = titulo;
            this.ambito = ambito;
            this.descripcion = descripcion;
            this.icono = icono;
        }

        public String titulo() { return titulo; }
        public String ambito() { return ambito; }
        public String descripcion() { return descripcion; }
        public String icono() { return icono; }

        public static Perfil de(Identidad identidad) {
            try {
                return valueOf(identidad.codigoRol());
            } catch (IllegalArgumentException e) {
                return GENERAL;
            }
        }
    }
}
