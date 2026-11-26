<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pagina" value="inicio" />
<fmt:setLocale value="es_PE" />
<!doctype html>
<html lang="es">
<head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Vista general · SIGMUD</title>
    <%@ include file="panel-recursos.jspf" %>
</head>
<body class="panel-body">
<div class="panel-layout">
    <%@ include file="barra.jspf" %>
    <div class="panel-main" data-panel-main>
        <%@ include file="cabecera.jspf" %>
        <main class="panel-content dashboard" id="contenido" tabindex="-1"
              data-perfil="<c:out value='${vistaInicio.perfil().name()}' />">
            <c:choose>
                <c:when test="${panelNoDisponible}">
                    <div class="panel-page-heading"><h1>Vista general</h1></div>
                    <div class="notice notice-error" role="alert">
                        No se pudieron consultar los datos de tu panel.
                        <a href="<c:url value='/inicio' />">Volver a intentar</a>
                    </div>
                </c:when>
                <c:otherwise>
                    <section class="dashboard-hero" aria-labelledby="dashboard-titulo">
                        <div class="dashboard-intro">
                            <p class="dashboard-ambito"><svg class="panel-icon" aria-hidden="true"><use href="${iconos}#${vistaInicio.perfil().icono()}" /></svg>
                                <c:out value="${vistaInicio.perfil().ambito()}" /></p>
                            <h1 id="dashboard-titulo"><c:out value="${vistaInicio.perfil().titulo()}" /></h1>
                            <p class="dashboard-description"><c:out value="${vistaInicio.perfil().descripcion()}" /></p>
                        </div>
                        <c:choose>
                            <c:when test="${not empty vistaInicio.buscador()}">
                                <c:url var="rutaBuscar" value="${vistaInicio.buscador().ruta()}" />
                                <form class="dashboard-buscar" action="<c:out value='${rutaBuscar}' />" method="get">
                                    <label for="consulta-inicio"><c:out value="${vistaInicio.buscador().etiqueta()}" /></label>
                                    <input name="tipo" type="hidden" value="<c:out value='${vistaInicio.buscador().tipo()}' />">
                                    <div>
                                        <input class="form-control" id="consulta-inicio" name="q" type="search" minlength="2" maxlength="180"
                                               placeholder="<c:out value='${vistaInicio.buscador().ejemplo()}' />" required>
                                        <button class="button button-primary" type="submit" aria-label="Buscar">
                                            <svg class="panel-icon" aria-hidden="true"><use href="${iconos}#search" /></svg>
                                        </button>
                                    </div>
                                </form>
                            </c:when>
                            <c:when test="${not empty vistaInicio.accesos()}">
                                <c:set var="destacado" value="${vistaInicio.accesos()[0]}" />
                                <c:url var="rutaDestacada" value="${destacado.ruta()}" />
                                <div class="dashboard-hero-side">
                                    <p>Bienvenido, <c:out value="${identidad.nombreVisible()}" /></p>
                                    <a class="button dashboard-hero-button" href="<c:out value='${rutaDestacada}' />">
                                        <c:out value="${destacado.titulo()}" /><svg class="panel-icon" aria-hidden="true"><use href="${iconos}#arrow" /></svg>
                                    </a>
                                </div>
                            </c:when>
                        </c:choose>
                    </section>

                    <section class="dashboard-indicators" aria-labelledby="indicadores-titulo">
                        <h2 id="indicadores-titulo">Indicadores</h2>
                        <c:choose>
                            <c:when test="${empty vistaInicio.indicadores()}"><div class="panel-surface panel-empty"><p>Tu perfil no tiene indicadores disponibles.</p></div></c:when>
                            <c:otherwise>
                                <div class="dashboard-kpis">
                                    <c:forEach items="${vistaInicio.indicadores()}" var="kpi">
                                        <article class="panel-surface dashboard-kpi ${kpi.alerta() ? 'dashboard-kpi-atencion' : ''}">
                                            <div class="dashboard-kpi-top">
                                                <h3><c:out value="${kpi.titulo()}" /></h3>
                                                <span class="panel-mark"><svg class="panel-icon" aria-hidden="true"><use href="${iconos}#${kpi.icono()}" /></svg></span>
                                            </div>
                                            <p class="dashboard-number"><fmt:formatNumber value="${kpi.valor()}" /></p>
                                            <div class="dashboard-kpi-bottom">
                                                <p><c:out value="${kpi.descripcion()}" /></p>
                                                <c:if test="${not empty kpi.ruta()}">
                                                    <c:url var="rutaKpi" value="${kpi.ruta()}" />
                                                    <a class="panel-icon-link" href="<c:out value='${rutaKpi}' />"
                                                       aria-label="Consultar: <c:out value='${kpi.titulo()}' />">
                                                        <svg class="panel-icon" aria-hidden="true"><use href="${iconos}#arrow" /></svg>
                                                    </a>
                                                </c:if>
                                            </div>
                                        </article>
                                    </c:forEach>
                                </div>
                            </c:otherwise>
                        </c:choose>
                    </section>

                    <div class="dashboard-board">
                        <div class="dashboard-column">
                            <c:forEach items="${vistaInicio.bloques()}" var="bloque" varStatus="posBloque">
                                <c:if test="${posBloque.index mod 2 eq 0}"><%@ include file="bloque-inicio.jspf" %></c:if>
                            </c:forEach>
                        </div>
                        <div class="dashboard-column">
                            <c:set var="perfilCodigo" value="${vistaInicio.perfil().name()}" />
                            <c:set var="priorizaBloque" value="${perfilCodigo eq 'REGISTRO_CIVIL' or perfilCodigo eq 'FISCALIZACION_DEFENSA_CIVIL' or perfilCodigo eq 'MERCADOS'}" />
                            <c:if test="${not priorizaBloque}"><%@ include file="atajos-inicio.jspf" %></c:if>
                            <c:forEach items="${vistaInicio.bloques()}" var="bloque" varStatus="posBloque">
                                <c:if test="${posBloque.index mod 2 ne 0}"><%@ include file="bloque-inicio.jspf" %></c:if>
                            </c:forEach>
                            <c:if test="${priorizaBloque}"><%@ include file="atajos-inicio.jspf" %></c:if>
                        </div>
                    </div>
                </c:otherwise>
            </c:choose>
        </main>
    </div>
</div>
</body>
</html>
