package pe.gob.sigmud.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Contrasenas {

    public static final int MINIMA = 15;
    public static final int MAXIMA = 128;
    private static final int ITERACIONES = 600_000;
    private static final int TAMANO_SAL = 16;
    private static final int TAMANO_CLAVE = 32;
    private static final String FORMATO = "pbkdf2-sha256";
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODIFICAR = Base64.getUrlDecoder();

    private Contrasenas() {
    }

    public static boolean longitudValida(char[] contrasena) {
        return contrasena.length >= MINIMA && contrasena.length <= MAXIMA;
    }

    public static String generar(char[] contrasena) {
        if (!longitudValida(contrasena)) {
            throw new IllegalArgumentException("Usa una contraseña de 15 a 128 caracteres.");
        }
        byte[] sal = new byte[TAMANO_SAL];
        ALEATORIO.nextBytes(sal);
        return FORMATO + "$" + ITERACIONES + "$" + BASE64.encodeToString(sal) + "$"
                + BASE64.encodeToString(derivar(contrasena, sal));
    }

    public static boolean verificar(char[] contrasena, String hash) {
        byte[] sal = new byte[TAMANO_SAL];
        byte[] esperada = new byte[TAMANO_CLAVE];
        boolean valido = false;
        if (hash != null) {
            String[] campos = hash.split("[$]", -1);
            if (campos.length == 4 && FORMATO.equals(campos[0])
                    && Integer.toString(ITERACIONES).equals(campos[1])) {
                try {
                    byte[] salGuardada = DECODIFICAR.decode(campos[2]);
                    byte[] claveGuardada = DECODIFICAR.decode(campos[3]);
                    if (salGuardada.length == TAMANO_SAL && claveGuardada.length == TAMANO_CLAVE) {
                        sal = salGuardada;
                        esperada = claveGuardada;
                        valido = true;
                    }
                } catch (IllegalArgumentException e) {
                    // Usa la derivación de comprobación aunque el dato guardado sea inválido.
                }
            }
        }
        return MessageDigest.isEqual(esperada, derivar(contrasena, sal)) && valido;
    }

    public static String huellaSesion(String hash) {
        try {
            byte[] huella = MessageDigest.getInstance("SHA-256").digest(hash.getBytes(StandardCharsets.US_ASCII));
            return BASE64.encodeToString(huella);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No está disponible el algoritmo de sesión.", e);
        }
    }

    private static byte[] derivar(char[] contrasena, byte[] sal) {
        PBEKeySpec especificacion = new PBEKeySpec(contrasena, sal, ITERACIONES, TAMANO_CLAVE * Byte.SIZE);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(especificacion).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No está disponible el algoritmo de contraseñas.", e);
        } finally {
            especificacion.clearPassword();
        }
    }
}
