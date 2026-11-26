package pe.gob.sigmud.model;

import java.time.LocalDateTime;

public record CitaMunicipal(long id, String codigo, long contribuyente, String titular, long ventanillaId,
        String ventanilla, long funcionarioId, String funcionario, String area, LocalDateTime inicio,
        LocalDateTime fin, String motivo, String estado) {
    public boolean programada() { return "PROGRAMADA".equals(estado); }
}
