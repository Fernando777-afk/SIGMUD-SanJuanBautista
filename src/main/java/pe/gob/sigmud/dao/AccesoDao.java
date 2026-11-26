package pe.gob.sigmud.dao;

import java.sql.SQLException;
import java.util.Optional;
import pe.gob.sigmud.model.Identidad;

public interface AccesoDao {

    record Credencial(long id, String usuario, String hash, boolean activa,
            int intentos, boolean bloqueada, boolean cicloVencido) {
    }

    Optional<Credencial> buscarCredencial(String usuario) throws SQLException;

    boolean registrarIntento(Credencial comprobada, boolean correcta, int maxIntentos,
            int minutosBloqueo) throws SQLException;

    Optional<Identidad> buscarIdentidad(long id, String huella) throws SQLException;
}
