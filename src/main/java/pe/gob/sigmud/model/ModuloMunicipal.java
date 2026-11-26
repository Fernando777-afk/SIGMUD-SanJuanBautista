package pe.gob.sigmud.model;

public enum ModuloMunicipal {
    SEGURIDAD("Usuarios y acceso", "shield", "Administración", "SEGURIDAD_ADMINISTRAR"),
    CONTRIBUYENTES("Contribuyentes y vecinos", "people", "Gestión municipal", "CONTRIBUYENTES_CONSULTAR"),
    TRAMITES("Trámite documentario", "file", "Gestión municipal", "TRAMITES_CONSULTAR"),
    RECLAMOS("Reclamos y emergencias", "alert", "Gestión municipal", "RECLAMOS_CONSULTAR"),
    CITAS("Citas municipales", "calendar", "Gestión municipal", "CITAS_CONSULTAR"),
    MERCADOS("Mercados y puestos", "store", "Gestión municipal", "MERCADOS_CONSULTAR"),
    ALMACEN("Almacén y logística", "box", "Gestión municipal", "ALMACEN_CONSULTAR"),
    REPORTES("Reportes de gestión", "chart", "Consultas", "REPORTES_CONSULTAR"),
    BUSQUEDAS("Búsquedas y clasificación", "search", "Consultas", null);

    private final String titulo;
    private final String icono;
    private final String grupo;
    private final String permiso;

    ModuloMunicipal(String titulo, String icono, String grupo, String permiso) {
        this.titulo = titulo;
        this.icono = icono;
        this.grupo = grupo;
        this.permiso = permiso;
    }

    public String titulo() { return titulo; }
    public String icono() { return icono; }
    public String grupo() { return grupo; }
    public String ruta() {
        return switch (this) {
            case SEGURIDAD -> "/usuarios";
            case CONTRIBUYENTES -> "/contribuyentes";
            case TRAMITES -> "/tramites";
            case RECLAMOS -> "/reclamos";
            case CITAS -> "/citas";
            case MERCADOS -> "/mercados";
            case ALMACEN -> "/almacen";
            case REPORTES -> "/reportes";
            case BUSQUEDAS -> "/busquedas";
        };
    }

    public boolean permitido(Identidad identidad) {
        return switch (this) {
            case SEGURIDAD -> identidad.administraUsuarios();
            case BUSQUEDAS -> java.util.Arrays.stream(values()).filter(modulo -> "Gestión municipal".equals(modulo.grupo))
                    .anyMatch(modulo -> modulo.permitido(identidad));
            default -> identidad.permisos().contains(permiso);
        };
    }
}
