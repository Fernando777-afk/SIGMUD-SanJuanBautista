"use strict";

function configurarMenu() {
    const panel = document.querySelector(".panel-layout");
    if (!panel) return;
    const abrir = panel.querySelector("[data-menu-open]");
    const cerrar = panel.querySelector("[data-menu-close]");
    const menu = panel.querySelector("#menu-panel");
    const fondo = panel.querySelector("[data-panel-overlay]");
    const contenido = panel.querySelector("[data-panel-main]");
    const pantallaPequena = window.matchMedia("(max-width: 875px)");
    panel.classList.add("panel-enhanced");

    function cambiarMenu(abierto, moverFoco = false) {
        const pequeno = pantallaPequena.matches;
        const visible = pequeno && abierto;
        panel.classList.toggle("menu-open", visible);
        abrir.hidden = !pequeno;
        cerrar.hidden = !pequeno;
        fondo.hidden = !visible;
        menu.inert = pequeno && !visible;
        contenido.inert = visible;
        abrir.setAttribute("aria-expanded", String(visible));
        if (moverFoco && pequeno) {
            (visible ? cerrar : abrir).focus();
        }
    }

    abrir.addEventListener("click", () => cambiarMenu(true, true));
    cerrar.addEventListener("click", () => cambiarMenu(false, true));
    fondo.addEventListener("click", () => cambiarMenu(false, true));
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && panel.classList.contains("menu-open")) {
            cambiarMenu(false, true);
        }
    });
    const enlaceSaltar = panel.querySelector(".panel-skip");
    enlaceSaltar.addEventListener("click", () => cambiarMenu(false));
    pantallaPequena.addEventListener("change", () => cambiarMenu(false));
    window.addEventListener("pageshow", () => cambiarMenu(false));
    cambiarMenu(false);
}

function configurarFormulario() {
    const formulario = document.querySelector("[data-form-cuenta]");
    if (!formulario) return;
    const boton = formulario.querySelector("[data-guardar-cuenta]");
    const texto = boton.querySelector("[data-guardar-texto]");
    const spinner = boton.querySelector(".panel-spinner");
    const dialogo = document.querySelector("[data-confirmar-cuenta]");
    let confirmado = false;

    formulario.addEventListener("submit", event => {
        const cambiaClave = formulario.elements.contrasena.value.length > 0;
        const deshabilita = formulario.dataset.activa === "true"
            && formulario.elements.activa.type === "checkbox" && !formulario.elements.activa.checked;
        const cambiaAlcance = formulario.elements.rol.value !== formulario.dataset.rolOriginal
            || formulario.elements.area.value !== formulario.dataset.areaOriginal;
        if (dialogo && typeof dialogo.showModal === "function" && !confirmado
                && (cambiaClave || deshabilita || cambiaAlcance)) {
            event.preventDefault();
            dialogo.returnValue = "";
            dialogo.showModal();
            return;
        }
        confirmado = false;
        formulario.setAttribute("aria-busy", "true");
        boton.disabled = true;
        spinner.hidden = false;
        texto.textContent = "Guardando…";
    });

    if (dialogo) {
        dialogo.addEventListener("close", () => {
            if (dialogo.returnValue === "guardar") {
                confirmado = true;
                formulario.requestSubmit();
                confirmado = false;
            }
        });
    }
    window.addEventListener("pageshow", () => {
        formulario.removeAttribute("aria-busy");
        boton.disabled = false;
        spinner.hidden = true;
        texto.textContent = "Guardar cuenta";
    });
}

configurarMenu();
configurarFormulario();
    
function configurarGestion() {
    const dialogo = document.querySelector("[data-dialogo-gestion]");
    if (!dialogo) return;
    const formularios = [...document.querySelectorAll("[data-gestion-form]")];
    let seleccionado = null;
    let confirmado = false;
    dialogo.addEventListener("close", () => {
        if (dialogo.returnValue === "confirmar" && seleccionado) {
            confirmado = true;
            seleccionado.requestSubmit();
            confirmado = false;
        }
        seleccionado = null;
    });
    for (const formulario of formularios) {
        const boton = formulario.querySelector("button[type=submit]");
        const texto = boton.textContent;
        formulario.addEventListener("submit", event => {
            if (formulario.dataset.confirmar && !confirmado && typeof dialogo.showModal === "function") {
                event.preventDefault();
                seleccionado = formulario;
                dialogo.returnValue = "";
                dialogo.querySelector("[data-dialogo-mensaje]").textContent = formulario.dataset.confirmar;
                dialogo.showModal();
                return;
            }
            formulario.setAttribute("aria-busy", "true");
            boton.disabled = true;
            boton.textContent = "Procesando…";
        });
        window.addEventListener("pageshow", () => {
            formulario.removeAttribute("aria-busy");
            boton.disabled = false;
            boton.textContent = texto;
        });
    }
}

configurarGestion();

const cola = document.querySelector("[data-cola-automatica]");
if (cola) {
    let actualizando = false;
    window.setInterval(async () => {
        if (actualizando || document.hidden || cola.contains(document.activeElement)) return;
        actualizando = true;
        try {
            const respuesta = await fetch(window.location.href, { credentials: "same-origin", cache: "no-store" });
            if (!respuesta.ok || respuesta.redirected) return;
            const vista = new DOMParser().parseFromString(await respuesta.text(), "text/html");
            const nuevaCola = vista.querySelector("[data-cola-automatica]");
            if (nuevaCola && !cola.contains(document.activeElement) && cola.innerHTML !== nuevaCola.innerHTML) {
                cola.replaceChildren(...nuevaCola.childNodes);
            }
        } catch {
            // La cola conserva los datos de la última consulta completa.
        } finally {
            actualizando = false;
        }
    }, 30000);
}

const imprimir = document.querySelector("[data-imprimir]");
if (imprimir) {
    imprimir.hidden = false;
    imprimir.addEventListener("click", () => window.print());
}
