package pe.gob.sigmud.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import pe.gob.sigmud.dao.impl.UsuarioJdbcDao.Cuenta;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.Pagina;
import pe.gob.sigmud.service.UsuariosService;
import pe.gob.sigmud.util.SeguridadWeb;

@WebServlet("/usuarios")
public final class UsuariosServlet extends HttpServlet {

    private static final String AVISO = "avisoUsuarios";
    private final UsuariosService usuarios = new UsuariosService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("aviso", request.getSession(false).getAttribute(AVISO));
        request.getSession(false).removeAttribute(AVISO);
        try {
            String editar = request.getParameter("editar");
            if (editar != null || request.getParameter("nueva") != null) {
                long id = editar == null ? 0 : Long.parseLong(editar);
                Cuenta cuenta = editar == null ? null : usuarios.buscar(id).orElse(null);
                if (editar != null && cuenta == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                request.setAttribute("cuenta", cuenta);
                request.setAttribute("roles", usuarios.roles());
                request.setAttribute("areas", usuarios.areas());
                request.getRequestDispatcher("/WEB-INF/views/acceso/cuenta.jsp").forward(request, response);
                return;
            }
            if (request.getParameter("permisos") != null) {
                request.setAttribute("permisos", usuarios.permisos());
                request.getRequestDispatcher("/WEB-INF/views/acceso/permisos.jsp").forward(request, response);
                return;
            }
            String texto = parametro(request, "q").strip();
            if (texto.length() > 160) {
                texto = texto.substring(0, 160);
            }
            String estado = parametro(request, "estado");
            Integer rol = enteroOpcional(parametro(request, "rol"));
            request.setAttribute("busqueda", texto);
            request.setAttribute("filtroRol", rol);
            request.setAttribute("filtroEstado", estado);
            request.setAttribute("roles", usuarios.roles());
            request.setAttribute("resultado", usuarios.listar(texto, rol, estado,
                    Pagina.numero(request.getParameter("pagina"))));
            request.getRequestDispatcher("/WEB-INF/views/acceso/usuarios.jsp").forward(request, response);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } catch (SQLException e) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        char[] contrasena = parametro(request, "contrasena").toCharArray();
        long id = 0;
        try {
            id = Long.parseLong(parametro(request, "id"));
            if (id < 0) {
                throw new IllegalArgumentException("La cuenta indicada no es válida.");
            }
            int rol = Integer.parseInt(parametro(request, "rol"));
            usuarios.guardar((Identidad) request.getAttribute("identidad"), id,
                    parametro(request, "usuario").strip(), parametro(request, "nombreVisible").strip(),
                    rol, "1".equals(parametro(request, "activa")), contrasena,
                    "1".equals(parametro(request, "desbloquear")), enteroOpcional(parametro(request, "area")));
            request.getSession(false).setAttribute(AVISO, "Se guardaron los cambios de la cuenta.");
            SeguridadWeb.redirigir(request, response, "/usuarios");
        } catch (NumberFormatException e) {
            avisarFormulario(request, response, id, "Selecciona una cuenta y un rol válidos.");
        } catch (IllegalArgumentException e) {
            avisarFormulario(request, response, id, e.getMessage());
        } catch (SecurityException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        } catch (SQLException e) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } finally {
            Arrays.fill(contrasena, '\0');
        }
    }

    private static void avisarFormulario(HttpServletRequest request, HttpServletResponse response, long id, String aviso) {
        request.getSession(false).setAttribute(AVISO, aviso);
        SeguridadWeb.redirigir(request, response, id > 0 ? "/usuarios?editar=" + id : "/usuarios?nueva=1");
    }

    private static String parametro(HttpServletRequest request, String nombre) {
        String valor = request.getParameter(nombre);
        return valor == null ? "" : valor;
    }

    private static Integer enteroOpcional(String valor) {
        return valor.isBlank() ? null : Integer.valueOf(valor);
    }
}
