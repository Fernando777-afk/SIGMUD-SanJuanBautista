# SIGMUD · Java Web

Proyecto Maven WAR para Java 17 y Apache Tomcat 10.1. La entrada de SIGMUD es el login institucional. El módulo Acceso y seguridad incluye sesiones, permisos y administración de cuentas.

La [guía operativa](GUIA_OPERATIVA.md) reúne el recorrido de los nueve módulos y las pruebas reproducibles con MySQL y Tomcat aislados. Para actualizar una instalación existente, consulta las [migraciones 06 a 08](database/README.md).

## Requisitos locales

- JDK 17 registrado como plataforma Java. Para compilar desde una terminal, configura `JAVA_HOME` con la ruta al JDK 17.
- Apache NetBeans con soporte para proyectos Maven y Java Web.
- Apache Tomcat 10.1 registrado como servidor en NetBeans.
- Maven. Puedes usar el Maven integrado en NetBeans. El proyecto se verificó con Maven 3.9.16.

La aplicación usa MySQL Server 8.0 con `sigmud_db`, los catálogos y la cuenta `sigmud_app` ya instalados.

## Abrir y ejecutar en Apache NetBeans

1. Usa **File → Open Project** y selecciona `C:\Proyectos\SIGMUD`, la carpeta que contiene `pom.xml`.
2. En **Tools → Java Platforms**, registra el JDK 17 si aún no aparece. Si NetBeans muestra la opción **Java Platform** en las propiedades del proyecto, selecciona el JDK 17.
3. En la pestaña **Services**, abre el menú de **Servers → Add Server**. Selecciona **Apache Tomcat or TomEE** y apunta a la carpeta de instalación de Tomcat 10.1.
4. En **Properties → Run** del proyecto, elige el servidor registrado y establece el **Context Path** en `/sigmud`.
5. Prepara la configuración JDBC local una vez como se indica abajo. En el uso diario abre NetBeans desde su icono y ejecuta **Run Project (F6)**. Abre [el login de SIGMUD](http://localhost:8080/sigmud/) con el puerto de tu instancia. La ruta `/estado` conserva la comprobación técnica del despliegue.

## Compilar y empaquetar

Desde la raíz del proyecto, ejecuta **Clean and Build** en NetBeans o usa Maven desde una terminal.

```powershell
mvn clean package
```

El resultado es `target/sigmud.war`. Para un despliegue manual, copia el WAR a la carpeta `webapps` de Tomcat 10.1 e inicia esa instancia con las variables JDBC.

La siguiente comprobación ejecuta el servicio y el DAO con H2 en memoria en modo MySQL. Usa la copia de H2 que Maven descargó durante la compilación.

```powershell
$h2 = "$env:USERPROFILE\.m2\repository\com\h2database\h2\2.3.232\h2-2.3.232.jar"
& "$env:JAVA_HOME\bin\java.exe" -cp "target\test-classes;target\sigmud\WEB-INF\classes;target\sigmud\WEB-INF\lib\*;$h2" pe.gob.sigmud.service.ComprobarAccesoJdbc
& "$env:JAVA_HOME\bin\java.exe" -cp "target\test-classes;target\sigmud\WEB-INF\classes;target\sigmud\WEB-INF\lib\*;$h2" pe.gob.sigmud.service.ComprobarPanelJdbc
```

## Primera instalación desde el ZIP

1. Extrae **todo** el proyecto en una carpeta privada y estable de tu cuenta de Windows. Instala JDK 17, NetBeans con Maven y soporte Java Web, Tomcat 10.1 y MySQL Server 8.0 con Workbench. Registra el JDK y Tomcat como se indicó arriba. Configura JAVA_HOME para JDK 17 o asegúrate de que java -version lo identifica desde el símbolo del sistema.
2. **Solo en una base nueva** prepara sigmud_db con utf8mb4 y utf8mb4_unicode_ci. Sigue **database/README.md** con la cuenta instaladora. Aplica 01, 02, 03 (necesario para las ocho cuentas demo), 04, 05 y las actualizaciones 06 a 08. Revisa cada resultado. En Workbench establece localmente la clave de sigmud_app para 127.0.0.1 y desbloquea la cuenta con TLS habilitado. En una instalación ya preparada conserva sus datos, sus cuentas y su matriz.
3. Desde NetBeans abre el proyecto y ejecuta **Clean and Build** en la instalación nueva, o usa **mvn verify** desde la raíz. El asistente utiliza las clases de prueba compiladas y las bibliotecas del WAR expandido.
4. Haz doble clic en **Configurar-SIGMUD.cmd**. Acepta la URL JDBC y el usuario sugeridos con Enter, o escribe los de tu instalación. Introduce la contraseña **de MySQL** con entrada oculta. El asistente consulta el catálogo roles mediante TLS y, si tiene éxito, escribe **config/sigmud-local.properties** con acceso para tu usuario y registra su ruta absoluta en **%USERPROFILE%\.sigmud\config.path**. La plantilla compartible es **config/sigmud-local.example.properties**. Guarda el proyecto en un directorio que otros usuarios no puedan modificar.
   Si ya tienes una sesión con las tres variables correctas, **Configurar-SIGMUD.cmd importar-entorno** permite migrarlas al archivo privado sin volver a escribir la clave. El asistente valida la cuenta con una consulta de lectura antes de guardar.
5. **Solo para una demostración recién instalada**, desde CMD en la raíz ejecuta **Configurar-SIGMUD.cmd cuentas**. Introduce dos veces la contraseña local elegida para cada cuenta (15 a 128 caracteres). Se reutilizan el hash PBKDF2 y la actualización condicional de SIGMUD. Cada cuenta queda confirmada individualmente. Si interrumpes el proceso, repite el comando para continuar. Las cuentas activas o con hash se omiten. Esta operación es independiente del arranque del servidor. Omítela en el equipo de la exposición si las ocho cuentas ya funcionan.
6. Ejecuta F6 y entra con el usuario de SIGMUD y su clave de aplicación. Configurar la conexión JDBC no habilita un usuario web. En otra computadora se requiere su propia instalación y configuración. Si mueves el proyecto, repite el paso 4 para registrar la nueva ubicación.

## Inicio diario desde el escritorio

Con MySQL en ejecución, abre NetBeans **desde su icono**, selecciona SIGMUD y pulsa **F6**. El IDE arranca su Tomcat o utiliza la instancia registrada. Abre **http://localhost:8080/sigmud/** y comprueba el perfil en Vista general. Usa el puerto de tu Tomcat si difiere de 8080. Para que MySQL esté disponible al encender Windows, puedes seleccionar el servicio de tu instancia en **services.msc → Propiedades → Tipo de inicio → Automático**. Administrar el servicio requiere permisos de Windows.

## Precedencia, diagnóstico y distribución

- Si existe alguna de **SIGMUD_DB_URL**, **SIGMUD_DB_USER** o **SIGMUD_DB_PASSWORD** en el proceso, se requieren las tres completas. Tienen prioridad como conjunto. Una configuración parcial produce un error y nunca mezcla credenciales de dos instalaciones.
- En ausencia de las tres variables, Java lee el localizador del perfil del usuario que ejecuta Tomcat y después el archivo privado UTF-8. Funciona con el IDE, con las herramientas de consola y con un WAR desplegado bajo la misma cuenta. Un servicio Tomcat con otra identidad necesita su propia configuración bajo esa identidad. Un perfil de Windows admite una instalación local seleccionada a la vez.
- Desde CMD en la carpeta del proyecto, **Configurar-SIGMUD.cmd comprobar** verifica la conexión de Java, versión, esquema y filas de roles con una consulta de lectura. Si falta el archivo o se movió la carpeta, ejecuta el asistente. Si MySQL rechaza la conexión, revisa el puerto, el servicio, el usuario y TLS. La URL admite únicamente jdbc:mysql://servidor:puerto/sigmud_db, sin credenciales ni parámetros. En conexiones remotas se verifica la identidad del servidor mediante el almacén de confianza de Java.
- Si el diagnóstico funciona y el login indica servicio indisponible, detén el Tomcat de NetBeans desde Services → Servers y cierra el IDE. Abre NetBeans desde el icono y pulsa F6. Comprueba que Tomcat usa la misma cuenta de Windows y que no hereda variables JDBC parciales o antiguas. Consulta el registro local de Tomcat para el diagnóstico. Si recibes 404, revisa el despliegue y el contexto /sigmud. La ruta /sigmud/estado comprueba el despliegue. Un login real comprueba también el acceso a MySQL.
- Distribuye el ZIP creado con **scripts/EmpaquetarDemo.ps1**, que selecciona fuentes, recursos, SQL, documentación y plantilla. El archivo privado y el localizador deben permanecer en el equipo. Conserva las claves de demostración por separado.

## Verificar acceso en el navegador

1. Abre `/sigmud/inicio` sin sesión. La aplicación debe llevarte a `/sigmud/login`.
2. Entra con una cuenta demo habilitada. Comprueba su nombre visible, su rol y el menú.
3. Con un operador, intenta abrir `/sigmud/usuarios`. Debe responder con acceso restringido. Con el administrador, abre la lista y verifica sus cuentas.
4. Prueba una contraseña incorrecta con una cuenta demo habilitada. Al quinto intento fallido, la cuenta se bloquea 15 minutos. Comprueba el estado desde otra sesión de administrador.
5. Prueba una cuenta demo inactiva y verifica el rechazo. Cierra sesión y vuelve a intentar abrir una ruta protegida.

## Revisar el panel municipal

Después de ingresar, abre [Vista general](http://localhost:8080/sigmud/inicio). Los indicadores se calculan al cargar la página con los permisos y el alcance de la sesión. La agenda muestra hasta tres citas programadas. Los registros con fecha futura quedan fuera de la actividad reciente.

Con un administrador, abre **Usuarios y acceso** para comprobar la búsqueda, los filtros por rol y estado y la paginación. En una pantalla estrecha, abre el menú desde la barra superior. La navegación muestra los módulos autorizados para el perfil.

## Estructura

- `src/main/java/pe/gob/sigmud/` contiene el controlador y los paquetes para modelo, servicios, DAO, utilidades, filtros y escuchadores.
- `src/main/webapp/WEB-INF/views/` contiene las vistas JSP internas de login, inicio y cuentas.
- `src/main/webapp/assets/` contiene los estilos, el JavaScript y las copias de la fachada y el escudo municipales.
- `database/` contiene los scripts de MySQL y la cuenta JDBC de la aplicación.
