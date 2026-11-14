/* Las confirmaciones usan datos escapados por Thymeleaf, nunca código generado. */
document.addEventListener('submit', evento => {
    const mensaje = evento.target.dataset.confirmar;
    if (mensaje && !window.confirm(mensaje)) evento.preventDefault();
});
