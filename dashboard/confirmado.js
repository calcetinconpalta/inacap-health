// Página a la que vuelve el enlace de confirmación de correo (redirect_to de la app).
// No usa la sesión que el enlace trae en la URL: la app revisa la confirmación por su cuenta.
// Solo se borra de la barra de direcciones para que no quede en el historial ni se comparta.
(function () {
  const fragmento = new URLSearchParams(location.hash.slice(1));
  const query = new URLSearchParams(location.search);
  const error = fragmento.get("error_description") || query.get("error_description");
  if (location.hash || location.search) history.replaceState(null, "", location.pathname);
  if (error) {
    document.getElementById("titulo").textContent = "No pudimos confirmar el correo";
    document.getElementById("sub").textContent =
      "El enlace no es válido o ya venció. Abre la app y vuelve a escribir tu correo para recibir uno nuevo.";
  }
})();
