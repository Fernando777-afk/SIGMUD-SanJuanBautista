package pe.gob.sigmud.model;

import java.util.List;

public record Pagina<T>(List<T> filas, int numero, boolean siguiente) {
    public static final int TAMANO = 12;
    public static final int MAXIMA = 100_000;

    public static int numero(String parametro) {
        try {
            return Math.max(1, Math.min(MAXIMA, Integer.parseInt(parametro)));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static int desplazamiento(int numero) {
        return (numero - 1) * TAMANO;
    }

    public static <T> Pagina<T> desde(List<T> filas, int numero) {
        boolean tieneSiguiente = filas.size() > TAMANO && numero < MAXIMA;
        return new Pagina<>(List.copyOf(filas.subList(0, Math.min(TAMANO, filas.size()))), numero, tieneSiguiente);
    }
}
