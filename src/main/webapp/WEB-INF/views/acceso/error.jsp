<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="inhabilitado" value="${requestScope['jakarta.servlet.error.status_code'] eq 503}" />
<!doctype html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><c:out value="${inhabilitado ? 'Servicio no disponible' : 'Acceso restringido'}" /> · SIGMUD</title>
    <%@ include file="estilos.jspf" %>
</head>
<body class="app-body">
    <main class="error-page">
        <h1><c:out value="${inhabilitado ? 'Servicio no disponible' : 'Acceso restringido'}" /></h1>
        <p><c:out value="${inhabilitado ? 'Inténtalo de nuevo en unos minutos.' : 'Tu sesión o tus permisos no permiten completar esta solicitud.'}" /></p>
        <a class="button button-primary" href="<c:url value='/login' />">Volver a SIGMUD</a>
    </main>
</body>
</html>
