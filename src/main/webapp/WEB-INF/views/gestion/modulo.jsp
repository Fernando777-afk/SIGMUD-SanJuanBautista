<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pagina" value="gestion" />
<!doctype html>
<html lang="es">
<head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title><c:out value="${pantalla.titulo()}" /> · SIGMUD</title>
    <%@ include file="../acceso/panel-recursos.jspf" %>
</head>
<body class="panel-body">
<div class="panel-layout">
    <%@ include file="../acceso/barra.jspf" %>
    <div class="panel-main" data-panel-main>
        <%@ include file="../acceso/cabecera.jspf" %>
        <main class="panel-content" id="contenido" tabindex="-1">
            <div class="panel-page-heading panel-gestion-titulo">
                <div class="panel-page-identity">
                    <span class="panel-mark"><svg class="panel-icon" aria-hidden="true"><use href="${iconos}#${moduloActual.icono()}" /></svg></span>
                    <div><h1><c:out value="${pantalla.titulo()}" /></h1><p><c:out value="${pantalla.descripcion()}" /></p></div>
                </div>
                <c:if test="${not empty pantalla.enlaces()}">
                    <div class="panel-gestion-enlaces">
                        <c:forEach items="${pantalla.enlaces()}" var="enlace">
                            <c:url var="destino" value="${enlace.destino()}" />
                            <c:set var="accionPrincipal" value="${fn:startsWith(enlace.texto(), 'Registrar') or enlace.texto() eq 'Programar cita'}" />
                            <a class="button ${accionPrincipal ? 'button-primary' : 'button-quiet'}" href="<c:out value='${destino}' />">
                                <c:if test="${accionPrincipal}"><svg class="panel-icon" aria-hidden="true"><use href="${iconos}#plus" /></svg></c:if>
                                <c:out value="${enlace.texto()}" />
                            </a>
                        </c:forEach>
                        <c:if test="${imprimirDisponible}"><button class="button button-quiet" type="button" data-imprimir hidden>Imprimir página</button></c:if>
                    </div>
                </c:if>
            </div>
            <c:if test="${not empty exito}"><p class="notice notice-success" role="status"><c:out value="${exito}" /></p></c:if>
            <c:if test="${not empty error}"><p class="notice notice-error" role="alert"><c:out value="${error}" /></p></c:if>
            <c:if test="${not empty notaModulo}"><p class="panel-muted panel-nota"><c:out value="${notaModulo}" /></p></c:if>
            <c:if test="${not empty pantalla.vistas()}">
                <nav class="panel-vistas" aria-label="Vistas del módulo">
                    <c:forEach items="${pantalla.vistas()}" var="vistaModulo">
                        <c:url var="rutaVista" value="${vistaModulo.destino()}" />
                        <a href="<c:out value='${rutaVista}' />" ${vistaModulo.activa() ? 'aria-current="page"' : ''}><c:out value="${vistaModulo.texto()}" /></a>
                    </c:forEach>
                </nav>
            </c:if>
            <c:if test="${not empty semanaAgenda}"><%@ include file="agenda-semanal.jspf" %></c:if>
            <c:url var="rutaModulo" value="${pantalla.ruta()}" />
            <c:if test="${not empty pantalla.filtros()}">
                <c:set var="formulario" value="${pantalla.filtros()}" />
                <c:set var="prefijo" value="consulta" />
                <form class="panel-query panel-surface panel-gestion-filtros" method="get" action="<c:out value='${rutaModulo}' />">
                    <h2 class="panel-filtros-titulo"><c:out value="${empty formulario.titulo() ? 'Filtros de consulta' : formulario.titulo()}" /></h2>
                    <%@ include file="campos.jspf" %>
                    <button class="button button-primary" type="submit">
                        <svg class="panel-icon" aria-hidden="true"><use href="${iconos}#search" /></svg>
                        <c:out value="${formulario.boton()}" />
                    </button>
                </form>
            </c:if>
            <c:set var="edicionEnFicha" value="${not empty pantalla.tablas() and not empty pantalla.formularios()}" />
            <div class="panel-flujo ${edicionEnFicha ? 'panel-flujo-ficha' : ''}">
                <c:if test="${not empty pantalla.tablas()}"><div class="panel-flujo-datos">
                    <c:forEach items="${pantalla.tablas()}" var="tabla" varStatus="pos">
                        <section class="panel-surface panel-gestion-tabla" aria-labelledby="tabla-${pos.index}"
                                 ${colaAutomatica ? 'data-cola-automatica' : ''}>
                            <div class="panel-surface-heading"><h2 id="tabla-${pos.index}"><c:out value="${tabla.titulo()}" /></h2></div>
                            <c:set var="textoVacio" value="${tabla.pagina() gt 0 ? pantalla.estadoVacio() : ''}" />
                            <%@ include file="tabla-municipal.jspf" %>
                            <c:if test="${not empty tabla.anterior() or not empty tabla.siguiente()}">
                                <div class="panel-pagination"><p class="panel-muted">Página ${tabla.pagina()}</p><div>
                                    <c:forEach items="${[tabla.anterior(), tabla.siguiente()]}" var="enlace">
                                        <c:if test="${not empty enlace}"><c:url var="destino" value="${enlace.destino()}" />
                                            <a class="button button-quiet" href="<c:out value='${destino}' />"><c:out value="${enlace.texto()}" /></a>
                                        </c:if>
                                    </c:forEach>
                                </div></div>
                            </c:if>
                        </section>
                    </c:forEach>

                </div></c:if>
                <c:if test="${not empty pantalla.formularios()}"><div class="panel-flujo-acciones">
                    <c:forEach items="${pantalla.formularios()}" var="formulario" varStatus="pos">
                        <c:set var="prefijo" value="edicion-${pos.index}" />
                        <form class="panel-surface panel-account-form panel-gestion-form" method="post" action="<c:out value='${rutaModulo}' />"
                              data-gestion-form data-confirmar="<c:out value='${formulario.confirmar()}' />">
                            <input type="hidden" name="csrf" value="<c:out value='${csrf}' />">
                            <input type="hidden" name="op" value="<c:out value='${formulario.operacion()}' />">
                            <h2><c:out value="${formulario.titulo()}" /></h2>
                            <div class="panel-campos">
                                <%@ include file="campos.jspf" %>
                            </div>
                            <div class="panel-form-actions"><button class="button button-primary" type="submit"><c:out value="${formulario.boton()}" /></button></div>
                        </form>
                    </c:forEach>

                </div></c:if>
            </div>
            <c:if test="${empty pantalla.tablas() and empty pantalla.formularios() and not empty pantalla.estadoVacio()}">
                <div class="panel-surface panel-empty">
                    <span class="panel-mark"><svg class="panel-icon" aria-hidden="true"><use href="${iconos}#${moduloActual.icono()}" /></svg></span>
                    <p><c:out value="${pantalla.estadoVacio()}" /></p>
                </div>
            </c:if>
        </main>
    </div>
</div>
<dialog class="panel-dialog" data-dialogo-gestion aria-labelledby="confirmar-gestion-titulo">
    <h2 id="confirmar-gestion-titulo">Confirmar operación</h2>
    <p data-dialogo-mensaje></p>
    <form method="dialog" class="panel-form-actions">
        <button class="button button-quiet" value="volver" autofocus>Volver</button>
        <button class="button button-primary" value="confirmar">Confirmar</button>
    </form>
</dialog>
</body>
</html>
