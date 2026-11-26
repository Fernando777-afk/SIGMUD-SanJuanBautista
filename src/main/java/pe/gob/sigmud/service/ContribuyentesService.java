package pe.gob.sigmud.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import pe.gob.sigmud.dao.impl.ContribuyenteJdbcDao;
import pe.gob.sigmud.model.Contribuyente;
import pe.gob.sigmud.model.Contribuyente.Tipo;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.util.Validacion;

public final class ContribuyentesService {
    private final ContribuyenteJdbcDao dao;

    public ContribuyentesService() { this(new ContribuyenteJdbcDao()); }
    public ContribuyentesService(ContribuyenteJdbcDao dao) { this.dao = dao; }

    public Pagina<Contribuyente> listar(Identidad usuario, String texto, String tipo, String estado, int pagina) throws SQLException {
        return listar(usuario, texto, tipo, estado, pagina, true);
    }

    public Pagina<Contribuyente> listar(Identidad usuario, String texto, String tipo, String estado, int pagina,
            boolean incluirPredios) throws SQLException {
        Validacion.permiso(usuario, "CONTRIBUYENTES_CONSULTAR");
        return dao.listar(texto, tipo.isBlank() ? null : Validacion.opcion(Tipo.class, tipo, "tipo"), estado, pagina, incluirPredios);
    }

    public Contribuyente consultar(Identidad usuario, long id) throws SQLException {
        Validacion.permiso(usuario, "CONTRIBUYENTES_CONSULTAR");
        return dao.buscar(id).orElseThrow(() -> new IllegalArgumentException("El contribuyente no está disponible."));
    }

    public List<String> predios(Identidad usuario, long id) throws SQLException {
        Validacion.permiso(usuario, "CONTRIBUYENTES_CONSULTAR");
        return dao.predios(id);
    }

    public long guardar(Identidad usuario, long id, String categoria, String dni, String ruc, String nombre,
            String telefono, String direccion, String nacimiento) throws SQLException {
        Validacion.permiso(usuario, "CONTRIBUYENTES_GESTIONAR");
        Tipo tipo = Validacion.opcion(Tipo.class, categoria, "tipo de persona");
        String documentoDni = Validacion.texto(dni, "DNI", 8, false);
        String documentoRuc = Validacion.texto(ruc, "RUC", 11, tipo == Tipo.JURIDICA);
        String numero = Validacion.texto(telefono, "teléfono", 20, false);
        LocalDate fecha = Validacion.fecha(nacimiento, "fecha de nacimiento");
        if (documentoDni != null && !documentoDni.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("El DNI debe contener ocho dígitos.");
        }
        if (documentoRuc != null && !documentoRuc.matches("[0-9]{11}")) {
            throw new IllegalArgumentException("El RUC debe contener once dígitos.");
        }
        if (tipo == Tipo.NATURAL && documentoDni == null && documentoRuc == null) {
            throw new IllegalArgumentException("Ingresa el DNI o RUC de la persona natural.");
        }
        if (tipo == Tipo.JURIDICA && (documentoDni != null || fecha != null)) {
            throw new IllegalArgumentException("Para personas jurídicas, deja vacíos el DNI y la fecha de nacimiento.");
        }
        if (fecha != null && fecha.isAfter(LocalDate.now(ZoneId.of("America/Lima")))) {
            throw new IllegalArgumentException("La fecha de nacimiento no puede estar en el futuro.");
        }
        if (numero != null && !numero.matches("[+]?[0-9]{7,15}")) {
            throw new IllegalArgumentException("Ingresa de siete a quince dígitos de teléfono, con + opcional.");
        }
        try {
            return dao.guardar(new Contribuyente(id, tipo, documentoDni, documentoRuc,
                    Validacion.texto(nombre, "nombre o razón social", 180, true), numero,
                    Validacion.texto(direccion, "dirección", 220, false), fecha, true));
        } catch (SQLException e) {
            if (duplicado(e)) throw new IllegalArgumentException("El DNI o RUC ya está registrado.");
            throw e;
        }
    }

    public void vincular(Identidad usuario, long id, String codigo) throws SQLException {
        Validacion.permiso(usuario, "CONTRIBUYENTES_GESTIONAR");
        String predio = Validacion.texto(codigo, "código de predio", 40, true);
        if (!predio.matches("[A-Za-z0-9][A-Za-z0-9._/-]*")) {
            throw new IllegalArgumentException("Usa letras, números, puntos, guiones o barras para el predio.");
        }
        try {
            dao.vincular(id, predio);
        } catch (SQLException e) {
            if (duplicado(e)) throw new IllegalArgumentException("El predio ya está asociado a este contribuyente.");
            throw e;
        }
    }

    public void baja(Identidad usuario, long id) throws SQLException {
        Validacion.permiso(usuario, "CONTRIBUYENTES_GESTIONAR");
        dao.baja(id);
    }

    private static boolean duplicado(SQLException e) {
        return e.getErrorCode() == 1062 || "23505".equals(e.getSQLState());
    }
}
