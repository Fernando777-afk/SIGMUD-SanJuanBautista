package pe.gob.sigmud.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import pe.gob.sigmud.service.AutenticacionService.Acceso;

public final class SeguridadWeb {

    public static final String ACCESO = "acceso";
    private static final String CSRF = "tokenCsrf";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private SeguridadWeb() {
    }

    public static Acceso acceso(HttpSession sesion) {
        return sesion == null ? null : (Acceso) sesion.getAttribute(ACCESO);
    }

    public static String token(HttpSession sesion) {
        synchronized (sesion) {
            String token = (String) sesion.getAttribute(CSRF);
            return token != null ? token : renovarToken(sesion);
        }
    }

    public static String renovarToken(HttpSession sesion) {
        byte[] valor = new byte[32];
        ALEATORIO.nextBytes(valor);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(valor);
        sesion.setAttribute(CSRF, token);
        return token;
    }

    public static boolean tokenValido(HttpServletRequest request, HttpSession sesion) {
        String esperado = sesion == null ? null : (String) sesion.getAttribute(CSRF);
        String recibido = request.getParameter("csrf");
        return esperado != null && recibido != null && recibido.length() == esperado.length()
                && MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                        recibido.getBytes(StandardCharsets.UTF_8));
    }

    public static void redirigir(HttpServletRequest request, HttpServletResponse response, String ruta) {
        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
        response.setHeader("Location", request.getContextPath() + ruta);
    }
}
