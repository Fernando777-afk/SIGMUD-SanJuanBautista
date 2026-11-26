package pe.gob.sigmud.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.model.ModuloMunicipal;
import pe.gob.sigmud.service.AutenticacionService;
import pe.gob.sigmud.service.AutenticacionService.Acceso;
import pe.gob.sigmud.util.SeguridadWeb;

@WebFilter("/*")
public final class SeguridadFilter implements Filter {

    private static final List<ModuloMunicipal> MODULOS = List.of(ModuloMunicipal.values());
    private final AutenticacionService autenticacion = new AutenticacionService();

    @Override
    public void doFilter(ServletRequest entrada, ServletResponse salida, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) entrada;
        HttpServletResponse response = (HttpServletResponse) salida;
        request.setCharacterEncoding("UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "same-origin");
        String ruta = request.getServletPath();

        if (ruta.startsWith("/assets/")) {
            chain.doFilter(request, response);
            return;
        }

        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; style-src 'self' https://cdn.jsdelivr.net; "
                + "img-src 'self' data:; object-src 'none'; frame-ancestors 'none'; "
                + "base-uri 'none'; form-action 'self'");

        HttpSession sesion = request.getSession(false);
        boolean publica = ruta.isEmpty() || "/".equals(ruta) || "/login".equals(ruta) || "/estado".equals(ruta);
        Acceso acceso = SeguridadWeb.acceso(sesion);
        if (!publica && acceso == null) {
            SeguridadWeb.redirigir(request, response, "/login");
            return;
        }
        if ("POST".equals(request.getMethod()) && !SeguridadWeb.tokenValido(request, sesion)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (publica || "/salir".equals(ruta)) {
            chain.doFilter(request, response);
            return;
        }

        Identidad identidad;
        try {
            identidad = autenticacion.consultarSesion(acceso).orElse(null);
        } catch (SQLException e) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        if (identidad == null) {
            sesion.invalidate();
            SeguridadWeb.redirigir(request, response, "/login");
            return;
        }
        if (("/usuarios".equals(ruta) || ruta.startsWith("/usuarios/")) && !identidad.administraUsuarios()) {
            request.setAttribute("identidad", identidad);
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        for (ModuloMunicipal modulo : MODULOS) {
            if (ruta.equals(modulo.ruta()) && !modulo.permitido(identidad)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
        request.setAttribute("identidad", identidad);
        request.setAttribute("modulos", MODULOS);
        request.setAttribute("csrf", SeguridadWeb.token(sesion));
        chain.doFilter(request, response);
    }
}
