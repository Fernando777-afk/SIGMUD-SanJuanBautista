package pe.gob.sigmud.model;

import java.time.LocalDateTime;

public record Tramite(long id, String codigo, long contribuyente, String titular, String tipo,
        String descripcion, LocalDateTime registrado, long version, long areaId, String area, Estado estado) {
    public enum Estado {
        RECIBIDO("Recibido"), EN_TRAMITE("En trámite"), ATENDIDO("Atendido"), ARCHIVADO("Archivado");
        private final String etiqueta;
        Estado(String etiqueta) { this.etiqueta = etiqueta; }
        public String etiqueta() { return etiqueta; }
    }

    public record Movimiento(long id, String area, Estado estado, String operador, LocalDateTime fecha, String observacion) {}
}
