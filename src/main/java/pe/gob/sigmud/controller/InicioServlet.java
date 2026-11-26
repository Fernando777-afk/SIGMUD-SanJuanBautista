package pe.gob.sigmud.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import pe.gob.sigmud.model.Identidad;
import pe.gob.sigmud.service.PanelService;

@WebServlet("/inicio")
public final class InicioServlet extends HttpServlet {

    private final PanelService panel = new PanelService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            Identidad identidad = (Identidad) request.getAttribute("identidad");
            request.setAttribute("vistaInicio", panel.vista(identidad));
        } catch (SQLException e) {
            getServletContext().log("SIGMUD: consulta del panel no disponible. Código JDBC=" + e.getErrorCode());
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("panelNoDisponible", true);
        }
        request.getRequestDispatcher("/WEB-INF/views/acceso/inicio.jsp").forward(request, response);
    }
}
