package pe.gob.sigmud.model;

import java.util.Set;

public record Identidad(long id, String usuario, String nombreVisible, int rolId,
        String codigoRol, String nombreRol, Set<String> permisos) {

    public Identidad {
        permisos = Set.copyOf(permisos);
    }

    public boolean administraUsuarios() {
        return "ADMINISTRADOR".equals(codigoRol) && permisos.contains("SEGURIDAD_ADMINISTRAR");
    }

    public String rolVisible() {
        return nombreRol;
    }
}
