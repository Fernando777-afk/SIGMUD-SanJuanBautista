<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pagina" value="usuarios" />
<!doctype html>
<html lang="es">
<head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Usuarios · SIGMUD</title>
    <%@ include file="panel-recursos.jspf" %>
</head>
<body class="panel-body">
<div class="panel-layout">
    <%@ include file="barra.jspf" %>
    <div class="panel-main" data-panel-main>
        <%@ include file="cabecera.jspf" %>
        <main class="panel-content" id="contenido" tabindex="-1">
            <div class="panel-page-heading panel-page-identity">
                <span class="panel-mark"><svg class="panel-icon" aria-hidden="true"><use href="${iconos}#shield" /></svg></span>
                <div><h1>Usuarios</h1><p>Administra las cuentas, las áreas y los roles de acceso.</p></div>
            </div>
            <c:if test="${not empty aviso}"><p class="notice notice-success" role="status"><c:out value="${aviso}" /></p></c:if>
            <section class="panel-surface" aria-labelledby="lista-titulo">
                <div class="panel-surface-heading">
                    <h2 id="lista-titulo">Cuentas registradas</h2>
                    <a class="button button-quiet" href="<c:url value='/usuarios'><c:param name='permisos' value='1' /></c:url>">Consultar permisos</a>
                </div>
                <form class="panel-query" method="get" action="<c:url value='/usuarios' />">
                    <div><label class="field-label" for="filtro-cuentas">Nombre visible o nombre de usuario</label>
                        <input class="form-control" id="filtro-cuentas" name="q" type="search" maxlength="160" value="<c:out value='${busqueda}' />"></div>
                    <div><label class="field-label" for="filtro-rol">Rol</label>
                        <select class="form-select" id="filtro-rol" name="rol">
                            <option value="">Todos</option>
                            <c:forEach items="${roles}" var="rol"><option value="${rol.id()}" ${filtroRol eq rol.id() ? 'selected' : ''}><c:out value="${rol.nombre()}" /></option></c:forEach>
                        </select></div>
                    <div><label class="field-label" for="filtro-estado">Estado de cuenta</label>
                        <select class="form-select" id="filtro-estado" name="estado">
                            <option value="">Todos</option>
                            <option value="activa" ${filtroEstado eq 'activa' ? 'selected' : ''}>Activa</option>
                            <option value="inactiva" ${filtroEstado eq 'inactiva' ? 'selected' : ''}>Inactiva</option>
                            <option value="bloqueada" ${filtroEstado eq 'bloqueada' ? 'selected' : ''}>Bloqueada</option>
                        </select></div>
                    <button class="button button-primary" type="submit">Filtrar</button>
                </form>
                <c:choose>
                    <c:when test="${empty resultado.filas()}"><p class="panel-empty">No hay cuentas que coincidan con los filtros.</p></c:when>
                    <c:otherwise>
                        <div class="panel-data-wrap"><table class="account-table panel-data-table" role="table" aria-label="Cuentas registradas">
                            <thead role="rowgroup"><tr role="row"><th scope="col" role="columnheader">Cuenta</th><th scope="col" role="columnheader">Rol</th><th scope="col" role="columnheader">Área</th><th scope="col" role="columnheader">Estado</th><th class="panel-row-action-head" scope="col" role="columnheader">Acción</th></tr></thead>
                            <tbody role="rowgroup"><c:forEach items="${resultado.filas()}" var="cuenta">
                                <tr role="row">
                                    <td role="cell"><strong><c:out value="${cuenta.nombreVisible()}" /></strong>
                                        <small><c:out value="${cuenta.usuario()}" /></small></td>
                                    <td role="cell" data-label="Rol"><c:out value="${cuenta.rol()}" /></td>
                                    <td role="cell" data-label="Área"><c:out value="${empty cuenta.area() ? 'Sin área asignada' : cuenta.area()}" /></td>
                                    <td role="cell" data-label="Estado">
                                        <c:choose>
                                            <c:when test="${not cuenta.activa()}"><span class="status-chip">Inactiva</span></c:when>
                                            <c:when test="${cuenta.bloqueada()}"><span class="status-chip status-blocked">Bloqueada</span></c:when>
                                            <c:otherwise><span class="status-chip status-ok">Activa</span></c:otherwise>
                                        </c:choose>
                                    </td>
                                    <td class="panel-row-action" role="cell"><a class="panel-edit" href="<c:url value='/usuarios'><c:param name='editar' value='${cuenta.id()}' /></c:url>"
                                           aria-label="Editar <c:out value='${cuenta.nombreVisible()}' />"><svg class="panel-icon" aria-hidden="true"><use href="${iconos}#edit" /></svg> Editar</a></td>
                                </tr>
                            </c:forEach></tbody>
                        </table></div>
                    </c:otherwise>
                </c:choose>
                <c:if test="${resultado.numero() gt 1 or resultado.siguiente()}"><div class="panel-pagination">
                    <p class="panel-muted">Página ${resultado.numero()}</p><div>
                        <c:if test="${resultado.numero() gt 1}">
                            <c:url var="anterior" value="/usuarios"><c:param name="q" value="${busqueda}" /><c:param name="rol" value="${filtroRol}" /><c:param name="estado" value="${filtroEstado}" /><c:param name="pagina" value="${resultado.numero() - 1}" /></c:url>
                            <a class="button button-quiet" href="<c:out value='${anterior}' />">Anterior</a>
                        </c:if>
                        <c:if test="${resultado.siguiente()}">
                            <c:url var="siguiente" value="/usuarios"><c:param name="q" value="${busqueda}" /><c:param name="rol" value="${filtroRol}" /><c:param name="estado" value="${filtroEstado}" /><c:param name="pagina" value="${resultado.numero() + 1}" /></c:url>
                            <a class="button button-quiet" href="<c:out value='${siguiente}' />">Siguiente</a>
                        </c:if>
                    </div>
                </div></c:if>
            </section>
        </main>
    </div>
</div>
</body>
</html>
