package pe.gob.sigmud.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public record PanelMunicipal(List<Medicion> mediciones, List<Actividad> actividad, List<Cita> proximasCitas) {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm", Locale.ROOT);

    public PanelMunicipal {
        mediciones = List.copyOf(mediciones);
        actividad = List.copyOf(actividad);
        proximasCitas = List.copyOf(proximasCitas);
    }

    public enum Indicador {
        CONTRIBUYENTES(ModuloMunicipal.CONTRIBUYENTES, "Contribuyentes registrados", "Personas y empresas vigentes", "blue"),
        TRAMITES(ModuloMunicipal.TRAMITES, "Trámites pendientes", "Recibidos o en trámite", "indigo"),
        RECLAMOS(ModuloMunicipal.RECLAMOS, "Casos por atender", "Reclamos y emergencias abiertos", "amber"),
        CITAS(ModuloMunicipal.CITAS, "Citas programadas", "Con fecha de inicio futura", "teal"),
        MERCADOS(ModuloMunicipal.MERCADOS, "Puestos ocupados", "Con asignación vigente", "blue"),
        ALMACEN(ModuloMunicipal.ALMACEN, "Materiales con stock bajo", "En el mínimo o por debajo", "amber");

        private final ModuloMunicipal modulo;
        private final String titulo;
        private final String descripcion;
        private final String color;

        Indicador(ModuloMunicipal modulo, String titulo, String descripcion, String color) {
            this.modulo = modulo;
            this.titulo = titulo;
            this.descripcion = descripcion;
            this.color = color;
        }

        public ModuloMunicipal modulo() { return modulo; }
        public String titulo() { return titulo; }
        public String descripcion() { return descripcion; }
        public String color() { return color; }

        public String ruta() {
            return modulo.ruta() + switch (this) {
                case RECLAMOS -> "?estado=ABIERTOS";
                case CITAS -> "?estado=PROGRAMADA";
                case MERCADOS -> "?estado=OCUPADO";
                case ALMACEN -> "?alerta=BAJO";
                default -> "";
            };
        }
    }

    public record Medicion(Indicador indicador, long total) {}

    public record Actividad(ModuloMunicipal modulo, String evento, String referencia, LocalDateTime fecha) {
        public String fechaVisible() {
            return fecha.format(FORMATO);
        }
    }

    public record Cita(String referencia, String ventanilla, LocalDateTime fecha) {
        public String fechaVisible() {
            return fecha.format(FORMATO);
        }
    }
}
