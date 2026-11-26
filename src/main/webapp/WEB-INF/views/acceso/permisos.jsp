<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pagina" value="permisos" />
<!doctype html>
<html lang="es">
<head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Permisos por rol · SIGMUD</title>
    <%@ include file="panel-recursos.jspf" %>
</head>
<body class="panel-body">
<div class="panel-layout">
    <%@ include file="barra.jspf" %>
    <div class="panel-main" data-panel-main>
        <%@ include file="cabecera.jspf" %>
        <main class="panel-content" id="contenido" tabindex="-1">
            <div class="panel-page-heading"><h1>Permisos por rol</h1><p>Consulta la matriz vigente de acceso a los módulos.</p></div>
            <c:forEach items="${permisos}" var="permiso" varStatus="fila">
                <c:if test="${fila.first or permisos[fila.index - 1].rol() ne permiso.rol()}">
                    <section class="panel-surface panel-permisos"><h2><c:out value="${permiso.rol()}" /></h2><ul>
                </c:if>
                <li><c:out value="${permiso.descripcion()}" /></li>
                <c:if test="${fila.last or permisos[fila.index + 1].rol() ne permiso.rol()}"></ul></section></c:if>
            </c:forEach>
            <a class="button button-quiet" href="<c:url value='/usuarios' />">Volver a usuarios</a>
        </main>
    </div>
</div>
</body>
</html>
