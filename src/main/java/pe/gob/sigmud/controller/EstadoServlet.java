package pe.gob.sigmud.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/estado")
public class EstadoServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("mensaje", "SIGMUD — Proyecto Java Web configurado");
        request.getRequestDispatcher("/WEB-INF/views/estado.jsp").forward(request, response);
    }
}
