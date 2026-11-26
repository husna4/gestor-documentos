/* Borradores locales al servidor. El navegador conserva únicamente identificadores y vistas. */
(() => {
    'use strict';
    const catalogo = window.mensajesSubida;
    const texto = (clave, ...argumentos) => catalogo[clave].replace(/\{(\d+)\}/g, (_, i) => String(argumentos[i]));
    const boton = document.getElementById('boton-subir');
    if (!boton) return;
    const form = boton.closest('form');
    const base = form.dataset.escaneosUrl;
    const maximo = Number(form.dataset.tamanoMaximo);
    const panel = document.getElementById('previsualizaciones');
    const resumen = document.getElementById('resultado-subida');
    const descartar = document.getElementById('descartar-todos');
    const escanear = document.getElementById('escanearFotos');
    const selectores = [...form.querySelectorAll('[data-selector-ficheros]')];
    const csrf = form.querySelector('input[name="_csrf"]');
    const entradas = [];
    let cola = Promise.resolve();
    let pendientes = 0;
    let guardando = false;
    const filtros = [
        ['enhanced', texto('mejorado')], ['original', texto('sinFiltro')],
        ['grayscale', texto('grises')], ['bw', texto('blancoNegro')], ['fuente', texto('original')]
    ];
    const esFoto = f => /\.(jpe?g|png|webp|hei[cf])$/i.test(f.name)
        || ['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif'].includes(f.type);

    function actualizar() {
        const ocupada = guardando || pendientes > 0;
        boton.disabled = ocupada || !entradas.length || entradas.some(e => !e.lista);
        boton.textContent = guardando ? texto('guardando') : pendientes ? texto('preparando')
            : entradas.length > 1 ? texto('guardarVarios', entradas.length) : texto('guardarUno');
        descartar.classList.toggle('d-none', !entradas.length);
        descartar.disabled = ocupada;
        document.querySelectorAll('[data-selector-idioma] select').forEach(s => { s.disabled = ocupada; });
        escanear.disabled = ocupada;
        selectores.forEach(s => { s.disabled = guardando; });
        entradas.forEach(e => {
            e.quitar.disabled = ocupada;
            if (e.selector) e.selector.disabled = ocupada || !e.id;
            if (e.reintentar) e.reintentar.disabled = ocupada;
        });
    }

    function encolar(tarea) {
        pendientes++;
        actualizar();
        const trabajo = cola.then(tarea);
        // Una petición fallida no bloquea las fotos siguientes.
        cola = trabajo.catch(() => {}).finally(() => { pendientes--; actualizar(); });
        return trabajo;
    }

    async function pedir(url, opciones = {}) {
        const respuesta = await fetch(url, {
            credentials: 'same-origin', cache: 'no-store', ...opciones,
            headers: { ...(opciones.method && opciones.method !== 'GET' ? {'X-CSRF-TOKEN': csrf.value} : {}),
                ...opciones.headers }
        }).catch(() => { throw new Error(texto('error')); });
        if (respuesta.redirected || respuesta.status === 401 || respuesta.status === 403) {
            throw new Error(texto('sesion'));
        }
        if (!respuesta.ok) {
            const datos = await respuesta.json().catch(() => null);
            const error = new Error(datos?.mensaje || (respuesta.status === 413
                ? texto('grande') : texto('error')));
            error.status = respuesta.status;
            throw error;
        }
        return respuesta;
    }

    function aviso(e, mensaje, error = false) {
        e.estado.textContent = mensaje;
        e.estado.className = `small mt-2 mb-0 ${error ? 'text-danger' : 'text-body-secondary'}`;
    }

    function crearElemento(tag, clase, texto) {
        const elemento = document.createElement(tag);
        elemento.className = clase;
        if (texto) elemento.textContent = texto;
        return elemento;
    }

    function mostrar(e, url) {
        e.imagen.src = url;
        e.ampliar.href = url;
        e.imagen.classList.remove('d-none');
        e.ampliar.classList.remove('d-none');
    }

    async function cambiarFiltro(e, variante) {
        const anterior = e.variante;
        aviso(e, texto('previsualizando'));
        try {
            const datos = await (await pedir(`${base}/${e.id}/filtro`, {
                method: 'POST', body: new URLSearchParams({variante})
            })).json();
            // Cargar la vista antes de confirmar la selección que se enviará al guardar.
            let url = e.originalUrl;
            if (variante !== 'fuente') {
                const blob = await (await pedir(`${base}/${e.id}/imagen?variante=${encodeURIComponent(variante)}`)).blob();
                url = URL.createObjectURL(blob);
            }
            if (e.procesadaUrl) URL.revokeObjectURL(e.procesadaUrl);
            e.procesadaUrl = variante === 'fuente' ? null : url;
            e.variante = variante;
            e.selector.value = variante;
            e.lista = true;
            e.comparando = false;
            e.comparar.textContent = texto('verOriginal');
            mostrar(e, url);
            aviso(e, datos.sinBordes ? texto('sinBordes')
                : variante === 'fuente' ? texto('guardarOriginal') : texto('guardarVersion'));
        } catch (error) {
            e.selector.value = anterior || 'fuente';
            aviso(e, texto('errorFiltro', error.message), true);
            // Si ya había una vista válida, conservar esa selección y su imagen.
            e.lista = Boolean(anterior);
            if (error.status === 404) {
                e.id = null; e.variante = null; e.lista = false;
                e.reintentar.classList.remove('d-none');
                aviso(e, texto('caducado'), true);
            }
        }
    }

    async function preparar(e) {
        try {
            aviso(e, texto('subiendo'));
            const body = new FormData();
            body.append('file', e.archivo);
            const info = await (await pedir(base, {method: 'POST', body})).json();
            e.id = info.id;
            e.titulo.textContent = info.nombre;
            await cambiarFiltro(e, escanear.checked ? 'enhanced' : 'fuente');
        } catch (error) {
            aviso(e, error.message, true);
            e.lista = false;
            e.reintentar.classList.remove('d-none');
        }
    }

    function retirar(e) {
        e.card.remove();
        entradas.splice(entradas.indexOf(e), 1);
        if (e.originalUrl) URL.revokeObjectURL(e.originalUrl);
        if (e.procesadaUrl) URL.revokeObjectURL(e.procesadaUrl);
    }

    async function eliminar(e) {
        try {
            if (e.id) {
                const respuesta = await fetch(`${base}/${e.id}`, {method: 'DELETE', credentials: 'same-origin',
                    headers: {'X-CSRF-TOKEN': csrf.value}}).catch(() => { throw new Error(texto('errorDescartar')); });
                if (!respuesta.ok && respuesta.status !== 404) throw new Error(texto('errorDescartar'));
            }
            retirar(e);
        } catch (error) { aviso(e, error.message, true); }
    }

    function agregar(archivo) {
        const e = {archivo, lista: false, variante: null};
        e.card = crearElemento('section', 'border rounded p-3');
        const cabecera = crearElemento('div', 'd-flex justify-content-between align-items-start gap-2');
        e.titulo = crearElemento('h2', 'h6 text-break mb-2', archivo.name);
        e.quitar = crearElemento('button', 'btn btn-sm btn-outline-danger flex-shrink-0', texto('quitar'));
        e.quitar.type = 'button';
        e.quitar.addEventListener('click', () => { encolar(() => eliminar(e)); });
        cabecera.append(e.titulo, e.quitar);
        e.card.append(cabecera);
        e.estado = crearElemento('p', 'small mb-0');
        if (archivo.size === 0 || archivo.size > maximo) {
            aviso(e, texto('vacioGrande', Math.round(maximo / 1048576)), true);
        } else if (esFoto(archivo)) {
            e.originalUrl = URL.createObjectURL(archivo);
            e.imagen = crearElemento('img', 'img-fluid d-block mx-auto rounded mb-2 d-none');
            e.imagen.alt = texto('previsualizacion', archivo.name);
            e.imagen.style.cssText = 'max-height:420px;object-fit:contain';
            e.imagen.addEventListener('error', () => {
                e.imagen.classList.add('d-none');
                aviso(e, texto('noVisible'));
            });
            const herramientas = crearElemento('div', 'd-flex flex-wrap gap-2 align-items-center');
            const etiqueta = crearElemento('label', 'form-label mb-0', texto('acabado'));
            e.selector = crearElemento('select', 'form-select');
            e.selector.id = 'filtro-' + entradas.length + '-' + Date.now();
            etiqueta.htmlFor = e.selector.id;
            filtros.forEach(([valor, texto]) => {
                const opcion = document.createElement('option'); opcion.value = valor; opcion.textContent = texto;
                e.selector.append(opcion);
            });
            e.selector.value = escanear.checked ? 'enhanced' : 'fuente';
            e.selector.addEventListener('change', () => {
                const valor = e.selector.value;
                encolar(() => cambiarFiltro(e, valor));
            });
            e.comparar = crearElemento('button', 'btn btn-sm btn-outline-secondary', texto('verOriginal'));
            e.comparar.type = 'button';
            e.comparar.addEventListener('click', () => {
                e.comparando = !e.comparando;
                mostrar(e, e.comparando ? e.originalUrl : e.procesadaUrl || e.originalUrl);
                e.comparar.textContent = e.comparando ? texto('volverVersion') : texto('verOriginal');
            });
            e.ampliar = crearElemento('a', 'btn btn-sm btn-outline-secondary d-none', texto('ampliar'));
            e.ampliar.target = '_blank'; e.ampliar.rel = 'noopener';
            e.reintentar = crearElemento('button', 'btn btn-sm btn-outline-primary d-none', texto('reintentar'));
            e.reintentar.type = 'button';
            e.reintentar.addEventListener('click', () => {
                e.reintentar.classList.add('d-none');
                encolar(() => preparar(e));
            });
            herramientas.append(etiqueta, e.selector, e.comparar, e.ampliar, e.reintentar);
            e.card.append(e.imagen, herramientas);
        } else {
            e.lista = true;
            aviso(e, texto('sinCambios'));
        }
        e.card.append(e.estado);
        entradas.push(e);
        panel.append(e.card);
        if (e.selector) encolar(() => preparar(e));
        actualizar();
    }

    selectores.forEach(selector => selector.addEventListener('change', () => {
        for (const archivo of selector.files) agregar(archivo);
        selector.value = ''; // Permite añadir más fotos, incluso tomadas desde la misma cámara.
    }));

    escanear.addEventListener('change', () => {
        const filtro = escanear.checked ? 'enhanced' : 'fuente';
        entradas.filter(e => e.id).forEach(e => encolar(() => cambiarFiltro(e, filtro)));
    });

    descartar.addEventListener('click', () => {
        encolar(async () => { for (const e of [...entradas]) await eliminar(e); });
    });

    form.addEventListener('submit', async evento => {
        evento.preventDefault();
        if (guardando || pendientes || !entradas.length || entradas.some(e => !e.lista)) return;
        if (!form.reportValidity()) return;
        guardando = true;
        actualizar();
        resumen.replaceChildren();
        resumen.className = 'alert alert-info';
        const campos = new FormData(form);
        const comunes = {
            seccionIds: campos.getAll('seccionIds').filter(Boolean).map(Number),
            personaId: campos.get('personaId') ? Number(campos.get('personaId')) : null,
            descripcion: campos.get('descripcion') || ''
        };
        // Guardar por archivo permite conservar en pantalla solo los que fallan y reintentarlos.
        for (const e of [...entradas]) {
            try {
                aviso(e, texto('guardandoVersion'));
                const body = new FormData();
                const datos = {...comunes, fotos: e.id ? [{id: e.id, variante: e.variante}] : []};
                body.append('datos', new Blob([JSON.stringify(datos)], {type: 'application/json'}));
                if (!e.id) body.append('ficheros', e.archivo);
                const respuesta = await pedir(`${base}/guardar`, {method: 'POST', body});
                const resultado = await respuesta.json();
                if (!resultado.subidos.length) {
                    const error = new Error(resultado.errores.join(' ') || texto('errorGuardar'));
                    error.caducado = respuesta.headers.get('X-Borrador-Caducado') === 'true';
                    throw error;
                }
                const mensaje = crearElemento('p', 'mb-1', texto('guardado', e.titulo.textContent));
                resumen.append(mensaje);
                retirar(e);
            } catch (error) {
                aviso(e, error.message, true);
                if (e.id && (error.caducado || error.status === 404)) {
                    e.id = null; e.variante = null; e.lista = false;
                    e.reintentar.classList.remove('d-none');
                }
                resumen.append(crearElemento('p', 'mb-1 text-danger', texto('errorArchivo', e.titulo.textContent, error.message)));
            }
        }
        guardando = false;
        resumen.className = entradas.length ? 'alert alert-warning' : 'alert alert-success';
        actualizar();
    });

    document.querySelectorAll('[data-selector-idioma]').forEach(selector => {
        selector.addEventListener('submit', evento => {
            if (guardando || pendientes) {
                evento.preventDefault();
                selector.querySelector('select').value = document.documentElement.lang;
                return;
            }
            if (entradas.length) {
                if (!window.confirm(texto('cambiarIdioma'))) {
                    evento.preventDefault();
                    selector.querySelector('select').value = document.documentElement.lang;
                } else salirPorIdioma = true;
            }
        });
    });
    let salirPorIdioma = false;
    window.addEventListener('beforeunload', evento => {
        if (entradas.length && !salirPorIdioma) { evento.preventDefault(); evento.returnValue = ''; }
    });
    actualizar();
})();
