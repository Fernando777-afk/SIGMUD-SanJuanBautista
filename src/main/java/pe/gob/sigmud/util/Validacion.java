package pe.gob.sigmud.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import pe.gob.sigmud.model.Identidad;

public final class Validacion {
    private Validacion() {}

    public static void permiso(Identidad identidad, String codigo) {
        if (!identidad.permisos().contains(codigo)) {
            throw new SecurityException("No tienes autorización para esta operación.");
        }
    }

    public static String texto(String valor, String nombre, int maximo, boolean obligatorio) {
        String limpio = valor == null ? "" : valor.strip();
        if (limpio.isEmpty()) {
            if (obligatorio) {
                throw new IllegalArgumentException("Completa el campo " + nombre + ".");
            }
            return null;
        }
        if (limpio.length() > maximo) {
            throw new IllegalArgumentException("El campo " + nombre + " admite hasta " + maximo + " caracteres.");
        }
        return limpio;
    }

    public static long id(String valor, String nombre) {
        try {
            long numero = Long.parseLong(valor);
            if (numero > 0) {
                return numero;
            }
        } catch (NumberFormatException e) {
            // Se muestra el mismo mensaje para valores vacíos y mal formados.
        }
        throw new IllegalArgumentException("Selecciona un valor válido en " + nombre + ".");
    }

    public static Long idOpcional(String valor, String nombre) {
        return valor == null || valor.isBlank() ? null : id(valor, nombre);
    }

    public static LocalDate fecha(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            LocalDate dia = LocalDate.parse(valor);
            if (dia.getYear() >= 1000 && dia.getYear() <= 9999) {
                return dia;
            }
        } catch (DateTimeParseException e) {
            // Se usa un mensaje adecuado para un formulario.
        }
        throw new IllegalArgumentException("Ingresa una fecha válida en " + nombre + ".");
    }

    public static LocalDateTime fechaHora(String valor, String nombre) {
        try {
            LocalDateTime momento = LocalDateTime.parse(valor);
            if (momento.getYear() >= 1000 && momento.getYear() <= 9999) {
                return momento;
            }
        } catch (DateTimeParseException | NullPointerException e) {
            // Se usa un mensaje adecuado para un formulario.
        }
        throw new IllegalArgumentException("Ingresa fecha y hora válidas en " + nombre + ".");
    }

    public static <T extends Enum<T>> T opcion(Class<T> opciones, String valor, String nombre) {
        try {
            return Enum.valueOf(opciones, valor);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Selecciona un valor válido en " + nombre + ".");
        }
    }

    public static String catalogo(List<String> opciones, String valor, String nombre, boolean obligatorio) {
        if (!obligatorio && (valor == null || valor.isBlank())) return null;
        if (!opciones.contains(valor)) throw new IllegalArgumentException("Selecciona un valor válido en " + nombre + ".");
        return valor;
    }

    public record Intervalo(LocalDate desde, LocalDate hastaExclusiva) {}

    public static Intervalo intervalo(String desde, String hasta) {
        LocalDate inicio = fecha(desde, "desde");
        LocalDate fin = fecha(hasta, "hasta");
        if (inicio != null && fin != null && fin.isBefore(inicio)) {
            throw new IllegalArgumentException("La fecha final debe ser igual o posterior a la inicial.");
        }
        return new Intervalo(inicio, fin == null || fin.equals(LocalDate.of(9999, 12, 31)) ? null : fin.plusDays(1));
    }

    public static String codigo(String prefijo) {
        return prefijo + "-" + LocalDate.now(ZoneId.of("America/Lima")).getYear() + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
    }
}
