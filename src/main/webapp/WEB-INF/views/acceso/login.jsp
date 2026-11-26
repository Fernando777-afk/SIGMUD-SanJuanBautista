<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="es">
<head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Acceso institucional · SIGMUD</title>
    <%@ include file="estilos.jspf" %>
    <script src="<c:url value='/assets/js/acceso.js' />" defer></script>
    <c:url var="iconosLogin" value="/assets/img/iconos-panel.svg" />
</head>
<body class="login-body">
    <div class="login-shell">
        <header class="login-brand">
            <img src="<c:url value='/assets/img/escudo-san-juan-bautista.jpg' />" width="900" height="900" alt="Escudo municipal">
            <div><strong>SIGMUD</strong><span>Municipalidad Distrital de San Juan Bautista</span></div>
            <p class="login-region">Huamanga, Ayacucho</p>
        </header>

        <main class="login-grid">
            <figure class="panorama">
                <img src="<c:url value='/assets/img/municipalidad-fondo.jpg' />" alt="Fachada de la Municipalidad Distrital de San Juan Bautista">
                <figcaption>
                    <span>Municipalidad Distrital de San Juan Bautista</span>
                    <p>Sistema de Gestión<br class="login-text-break"> Municipal Distrital</p>
                </figcaption>
            </figure>

            <section class="login-main" aria-labelledby="login-titulo">
                <div class="login-card">
                    <header class="login-heading">
                        <p class="login-kicker">Acceso institucional</p>
                        <h1 id="login-titulo">Bienvenido a SIGMUD</h1>
                        <p class="login-intro">Inicia sesión con tu cuenta institucional.</p>
                    </header>
                    <c:if test="${not empty aviso}">
                        <p class="notice notice-error" role="alert"><c:out value="${aviso}" /></p>
                    </c:if>
                    <form method="post" action="<c:url value='/login' />" id="login-form">
                        <input type="hidden" name="csrf" value="<c:out value='${csrf}' />">
                        <div class="login-field">
                            <label class="field-label" for="usuario">Usuario</label>
                            <div class="login-control">
                                <svg class="login-icon" aria-hidden="true"><use href="${iconosLogin}#user" /></svg>
                                <input class="form-control" type="text" id="usuario" name="usuario" required maxlength="64"
                                       autocomplete="username" autocapitalize="none" spellcheck="false"
                                       value="<c:out value='${usuario}' />">
                            </div>
                        </div>
                        <div class="login-field">
                            <label class="field-label" for="contrasena">Contraseña</label>
                            <div class="login-control password-field">
                                <svg class="login-icon" aria-hidden="true"><use href="${iconosLogin}#shield" /></svg>
                                <input class="form-control" id="contrasena" name="contrasena" type="password"
                                       autocomplete="current-password" required maxlength="128">
                                <button class="password-button" type="button" id="ver-contrasena" hidden
                                        aria-controls="contrasena" aria-pressed="false">Mostrar</button>
                            </div>
                        </div>

                        <button class="button button-primary login-submit" type="submit" id="ingresar">
                            <span class="button-label">Ingresar</span>
                            <svg class="login-icon button-arrow" aria-hidden="true"><use href="${iconosLogin}#arrow" /></svg>
                            <span class="loading-ring" aria-hidden="true"></span>
                        </button>
                        <span class="visually-hidden" id="estado-acceso" role="status"></span>
                    </form>
                </div>
            </section>
        </main>
    </div>
</body>
</html>
