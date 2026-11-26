package pe.gob.sigmud.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.gob.sigmud.util.SeguridadWeb;

@WebServlet("/salir")
public final class SalirServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        request.getSession(false).invalidate();
        Cookie cookie = new Cookie("JSESSIONID", "");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
        response.addCookie(cookie);
        SeguridadWeb.redirigir(request, response, "/login");
    }
}
