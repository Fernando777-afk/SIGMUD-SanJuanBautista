package pe.gob.sigmud.model;

import java.time.LocalDateTime;

public record CasoMunicipal(long id, String codigo, String tipo, String prioridad, String estado, long areaId, String area,
        Long contribuyente, String titular, String descripcion, String ubicacion, LocalDateTime registro, LocalDateTime atencion) {
    public boolean abierto() { return "PENDIENTE".equals(estado) || "EN_ATENCION".equals(estado); }
}
