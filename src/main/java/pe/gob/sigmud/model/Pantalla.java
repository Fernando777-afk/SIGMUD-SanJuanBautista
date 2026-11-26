package pe.gob.sigmud.model;

import java.util.ArrayList;
import java.util.List;

public final class Pantalla {
    private final String titulo;
    private final String descripcion;
    private final String ruta;
    private final List<Enlace> enlaces = new ArrayList<>();
    private final List<Vista> vistas = new ArrayList<>();
    private String estadoVacio;
    private final List<Formulario> formularios = new ArrayList<>();
    private final List<Tabla> tablas = new ArrayList<>();
    private Formulario filtros;

    public Pantalla(String titulo, String descripcion, String ruta) {
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.ruta = ruta;
    }

    public String titulo() { return titulo; }
    public String descripcion() { return descripcion; }
    public String ruta() { return ruta; }
    public List<Enlace> enlaces() { return enlaces; }
    public List<Vista> vistas() { return vistas; }
    public String estadoVacio() { return estadoVacio; }
    public void estadoVacio(String mensaje) { estadoVacio = mensaje; }

    public void vista(String texto, String destino, boolean activa) {
        vistas.add(new Vista(texto, destino, activa));
    }

    public List<Formulario> formularios() { return formularios; }
    public List<Tabla> tablas() { return tablas; }
    public Formulario filtros() { return filtros; }
    public void filtros(Formulario formulario) { filtros = formulario; }

    public record Opcion(String valor, String etiqueta) {
        public Opcion(long valor, String etiqueta) { this(Long.toString(valor), etiqueta); }
    }

    public record Campo(String nombre, String etiqueta, String tipo, Object valor, boolean obligatorio,
            int longitud, List<Opcion> opciones) {
        public static Campo texto(String nombre, String etiqueta, Object valor, boolean obligatorio, int longitud) {
            return new Campo(nombre, etiqueta, "text", valor, obligatorio, longitud, List.of());
        }

        public static Campo sugerencias(String nombre, String etiqueta, Object valor, int longitud, List<Opcion> opciones) {
            return new Campo(nombre, etiqueta, "text", valor, true, longitud, List.copyOf(opciones));
        }

        public static Campo tipo(String nombre, String etiqueta, String tipo, Object valor, boolean obligatorio) {
            return new Campo(nombre, etiqueta, tipo, valor, obligatorio, 0, List.of());
        }

        public static Campo seleccionar(String nombre, String etiqueta, Object valor, boolean obligatorio, List<Opcion> opciones) {
            return new Campo(nombre, etiqueta, "select", valor, obligatorio, 0, List.copyOf(opciones));
        }

        public static Campo oculto(String nombre, Object valor) {
            return tipo(nombre, "", "hidden", valor, false);
        }

        public static Campo parrafo(String nombre, String etiqueta, Object valor, boolean obligatorio, int longitud) {
            return new Campo(nombre, etiqueta, "textarea", valor, obligatorio, longitud, List.of());
        }
    }

    public record Vista(String texto, String destino, boolean activa) {}

    public record Enlace(String texto, String destino) {}

    public record Formulario(String titulo, String boton, String operacion, String confirmar, List<Campo> campos) {}

    public record Fila(List<String> celdas, Enlace enlace) {
        public static Fila texto(String... valores) { return nueva(null, valores); }

        public static Fila nueva(Enlace enlace, String... valores) {
            return new Fila(java.util.Arrays.stream(valores).map(valor -> valor == null ? "—" : valor).toList(), enlace);
        }
    }

    public record Tabla(String titulo, List<String> columnas, List<Fila> filas, boolean acciones,
            Enlace anterior, Enlace siguiente, int pagina) {
        public Tabla(String titulo, List<String> columnas, List<Fila> filas, boolean acciones) {
            this(titulo, columnas, filas, acciones, null, null, 0);
        }
    }
}
