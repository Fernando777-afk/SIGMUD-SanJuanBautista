package pe.gob.sigmud.model;

import java.time.LocalDate;

public record Contribuyente(long id, Tipo tipo, String dni, String ruc, String nombre, String telefono,
        String direccion, LocalDate nacimiento, boolean activo) {
    public enum Tipo { NATURAL, JURIDICA }

    public String documento() {
        if (dni == null) return "RUC " + ruc;
        return "DNI " + dni + (ruc == null ? "" : " · RUC " + ruc);
    }
}
