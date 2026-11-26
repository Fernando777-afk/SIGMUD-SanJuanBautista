const formulario = document.getElementById("login-form");
const contrasena = document.getElementById("contrasena");
const alternar = document.getElementById("ver-contrasena");
const enviar = document.getElementById("ingresar");
const rotulo = enviar.querySelector(".button-label");
const estado = document.getElementById("estado-acceso");

alternar.hidden = false;
alternar.addEventListener("click", () => {
    const visible = contrasena.type === "password";
    contrasena.type = visible ? "text" : "password";
    alternar.textContent = visible ? "Ocultar" : "Mostrar";
    alternar.setAttribute("aria-pressed", String(visible));
});

formulario.addEventListener("submit", () => {
    enviar.disabled = true;
    formulario.setAttribute("aria-busy", "true");
    rotulo.textContent = "Ingresando…";
    estado.textContent = "Comprobando tus datos.";
});

window.addEventListener("pageshow", () => {
    enviar.disabled = false;
    formulario.removeAttribute("aria-busy");
    rotulo.textContent = "Ingresar";
    estado.textContent = "";
});
