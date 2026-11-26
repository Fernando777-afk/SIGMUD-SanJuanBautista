import java.nio.file.Path;
import java.nio.file.Files;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.scan.StandardJarScanner;

public final class ServidorPruebas {
    public static void main(String[] argumentos) throws Exception {
        Path base = Path.of(argumentos[0]).toAbsolutePath();
        Path war = Path.of(argumentos[1]).toAbsolutePath();
        Files.createDirectories(base.resolve("webapps"));
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(base.toString());
        tomcat.setPort(18091);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        var contexto = tomcat.addWebapp("/sigmud", war.toString());
        if (contexto.getJarScanner() instanceof StandardJarScanner escaner) {
            escaner.setScanClassPath(false);
            escaner.setScanManifest(false);
        }
        tomcat.start();
        tomcat.getServer().await();
    }
}
