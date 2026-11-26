<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pagina" value="cuenta" />
<c:set var="edicion" value="${not empty cuenta}" />
<c:set var="propia" value="${edicion and cuenta.id() eq identidad.id()}" />
<!doctype html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Cuenta de usuario · SIGMUD</title>
    <%@ include file="panel-recursos.jspf" %>
</head>
<body class="app-body panel-body">
    <div class="panel-layout">
        <%@ include file="barra.jspf" %>
        <div class="panel-main" data-panel-main>
            <%@ include file="cabecera.jspf" %>
            <main class="panel-content" id="contenido" tabindex="-1">
                <div class="panel-page-heading">
                    <h1><c:out value="${edicion ? 'Editar cuenta' : 'Nueva cuenta'}" /></h1>
                    <p><c:out value="${edicion ? 'Revisa el perfil y la habilitación de esta cuenta.' : 'Configura las credenciales y el rol de acceso de la nueva cuenta.'}" /></p>
                </div>
                <c:if test="${not empty aviso}">
                    <p class="notice notice-error" role="alert"><c:out value="${aviso}" /></p>
                </c:if>
                <form class="panel-surface panel-account-form panel-edicion-usuario" method="post" action="<c:url value='/usuarios' />"
                      data-form-cuenta data-edicion="${edicion}" data-activa="${edicion and cuenta.activa()}"
                      data-rol-original="${cuenta.rolId()}" data-area-original="${cuenta.areaId()}">
                    <input type="hidden" name="csrf" value="<c:out value='${csrf}' />">
                    <input type="hidden" name="id" value="<c:out value='${edicion ? cuenta.id() : 0}' />">
                    <div class="panel-campos panel-campos-usuario">
                        <div>
                            <label class="field-label" for="usuario">Usuario</label>
                            <input class="form-control" id="usuario" name="usuario" required minlength="3" maxlength="64"
                                   ${edicion and cuenta.identificadorReservado() ? 'readonly' : ''}
                                   autocomplete="off" value="<c:out value='${cuenta.usuario()}' />">
                        </div>
                        <div>
                            <label class="field-label" for="nombre-visible">Nombre visible</label>
                            <input class="form-control" id="nombre-visible" name="nombreVisible" required maxlength="160"
                                   value="<c:out value='${edicion ? cuenta.nombreVisible() : ""}' />">
                        </div>
                        <div>
                            <label class="field-label" for="rol">Rol</label>
                            <c:choose>
                                <c:when test="${propia}">
                                    <input class="form-control" id="rol" readonly value="<c:out value='${cuenta.rol()}' />">
                                    <input type="hidden" name="rol" value="<c:out value='${cuenta.rolId()}' />">
                                </c:when>
                                <c:otherwise>
                                    <select class="form-select" id="rol" name="rol" required>
                                        <option value="">Selecciona un rol</option>
                                        <c:forEach items="${roles}" var="rol">
                                            <option value="<c:out value='${rol.id()}' />" ${edicion and rol.id() eq cuenta.rolId() ? 'selected' : ''}><c:out value="${rol.nombre()}" /></option>
                                        </c:forEach>
                                    </select>
                                </c:otherwise>
                            </c:choose>
                        </div>
                        <div>
                            <label class="field-label" for="area">Área municipal</label>
                            <select class="form-select" id="area" name="area">
                                <option value="">Sin área asignada</option>
                                <c:set var="areaVigente" value="false" />
                                <c:forEach items="${areas}" var="area">
                                    <c:if test="${area.id() eq cuenta.areaId()}"><c:set var="areaVigente" value="true" /></c:if>
                                    <option value="${area.id()}" ${area.id() eq cuenta.areaId() ? 'selected' : ''}><c:out value="${area.nombre()}" /></option>
                                </c:forEach>
                                <c:if test="${edicion and not areaVigente and not empty cuenta.areaId()}">
                                    <option value="${cuenta.areaId()}" selected><c:out value="${cuenta.area()}" /> (inactiva)</option>
                                </c:if>
                            </select>
                        </div>
                        <div>
                            <label class="field-label" for="contrasena"><c:out value="${edicion ? 'Nueva contraseña' : 'Contraseña'}" /></label>
                            <input class="form-control" type="password" id="contrasena" name="contrasena" ${edicion ? '' : 'required'}
                                   minlength="15" maxlength="128" autocomplete="new-password" aria-describedby="ayuda-clave">
                            <p class="field-help" id="ayuda-clave">
                                Entre 15 y 128 caracteres.
                                <c:if test="${edicion}">Déjala en blanco para conservar la contraseña actual.</c:if>
                            </p>
                        </div>
                        <div>
                            <c:choose>
                                <c:when test="${propia}">
                                    <input type="hidden" name="activa" value="1">
                                    <p class="field-help">Tu cuenta conserva el rol y la habilitación actuales.</p>
                                </c:when>
                                <c:otherwise>
                                    <label class="check-row"><input type="checkbox" name="activa" value="1" ${edicion and cuenta.activa() ? 'checked' : ''}> Cuenta habilitada</label>
                                </c:otherwise>
                            </c:choose>
                            <c:if test="${edicion and cuenta.bloqueada()}">
                                <label class="check-row"><input type="checkbox" name="desbloquear" value="1"> Restablecer intentos fallidos</label>
                            </c:if>
                        </div>
                    </div>
                    <div class="panel-form-actions">
                        <a class="button button-quiet" href="<c:url value='/usuarios' />">Cancelar</a>
                        <button class="button button-primary" type="submit" data-guardar-cuenta>
                            <span class="panel-spinner" aria-hidden="true" hidden></span><span data-guardar-texto>Guardar cuenta</span>
                        </button>
                    </div>
                </form>
                <c:if test="${edicion}">
                    <dialog class="panel-dialog" data-confirmar-cuenta aria-labelledby="confirmar-titulo">
                        <h2 id="confirmar-titulo">Confirmar cambios de acceso</h2>
                        <p>Verifica el rol, el área y la habilitación. Cambiar la contraseña o deshabilitar la cuenta cerrará sus sesiones activas.</p>
                        <form method="dialog" class="panel-form-actions">
                            <button class="button button-quiet" value="cancelar" autofocus>Revisar</button>
                            <button class="button button-primary" value="guardar">Confirmar y guardar</button>
                        </form>
                    </dialog>
                </c:if>
            </main>
        </div>
    </div>
</body>
</html>
