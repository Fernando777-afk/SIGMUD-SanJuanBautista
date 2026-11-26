package pe.gob.sigmud.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import pe.gob.sigmud.service.AutenticacionService;
import pe.gob.sigmud.service.AutenticacionService.Acceso;
import pe.gob.sigmud.util.ConexionBD.ConfiguracionJdbcException;
import pe.gob.sigmud.util.Contrasenas;
import pe.gob.sigmud.util.SeguridadWeb;

@WebServlet("/login")
public final class LoginServlet extends HttpServlet {

    private static final String AVISO = "avisoLogin";
    private static final String USUARIO = "usuarioLogin";
    private final AutenticacionService autenticacion = new AutenticacionService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (SeguridadWeb.acceso(request.getSession(false)) != null) {
            SeguridadWeb.redirigir(request, response, "/inicio");
            return;
        }
        HttpSession sesion = request.getSession();
        request.setAttribute("aviso", sesion.getAttribute(AVISO));
        request.setAttribute("usuario", sesion.getAttribute(USUARIO));
        sesion.removeAttribute(AVISO);
        sesion.removeAttribute(USUARIO);
        mostrar(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession sesion = request.getSession(false);
        if (SeguridadWeb.acceso(sesion) != null) {
            SeguridadWeb.redirigir(request, response, "/inicio");
            return;
        }
        String parametroUsuario = request.getParameter("usuario");
        String usuario = parametroUsuario == null ? "" : parametroUsuario.strip();
        String entrada = request.getParameter("contrasena");
        if (usuario.isEmpty() || usuario.length() > 64 || entrada == null
                || entrada.isEmpty() || entrada.length() > Contrasenas.MAXIMA) {
            volver(request, response, usuario, "Ingresa un usuario y una contraseña válidos.");
            return;
        }

        char[] contrasena = entrada.toCharArray();
        try {
            Acceso acceso = autenticacion.autenticar(usuario, contrasena).orElse(null);
            if (acceso == null) {
                volver(request, response, usuario,
                        "No pudimos iniciar sesión. Revisa tus datos. Si hubo varios intentos, espera 15 minutos.");
                return;
            }
            request.changeSessionId();
            SeguridadWeb.renovarToken(sesion);
            sesion.setAttribute(SeguridadWeb.ACCESO, acceso);
            SeguridadWeb.redirigir(request, response, "/inicio");
        } catch (SQLException e) {
            registrarFalloJdbc(e);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("usuario", usuario);
            request.setAttribute("aviso", "El servicio de acceso no está disponible. Inténtalo más tarde.");
            mostrar(request, response);
        } finally {
            Arrays.fill(contrasena, '\0');
        }
    }

    private void registrarFalloJdbc(SQLException fallo) {
        String detalle;
        if (fallo instanceof ConfiguracionJdbcException) {
            // Esta excepción solo contiene mensajes de configuración definidos por SIGMUD.
            detalle = fallo.getMessage();
        } else {
            String estadoSql = fallo.getSQLState();
            if (estadoSql == null || !estadoSql.matches("[A-Z0-9]{5}")) {
                estadoSql = "desconocido";
            }
            detalle = "SQLState=" + estadoSql + ", código JDBC=" + fallo.getErrorCode();
        }
        getServletContext().log("SIGMUD: error JDBC durante el inicio de sesión. " + detalle);
    }

    private static void mostrar(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("csrf", SeguridadWeb.token(request.getSession()));
        request.getRequestDispatcher("/WEB-INF/views/acceso/login.jsp").forward(request, response);
    }

    private static void volver(HttpServletRequest request, HttpServletResponse response, String usuario, String aviso) {
        HttpSession sesion = request.getSession();
        sesion.setAttribute(AVISO, aviso);
        sesion.setAttribute(USUARIO, usuario.length() <= 64 ? usuario : "");
        SeguridadWeb.redirigir(request, response, "/login");
    }
}
