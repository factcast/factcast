/* All application assets are local; no frontend build or CDN is required. */
window.FactCastUI = {
  saveState: function (state) {
    const url = new URL(window.location.href);
    url.search = '';
    url.searchParams.set('state', state);
    window.history.replaceState(null, '', url);
  }
};
document.addEventListener('click', function (event) {
  const toggle = event.target.closest('.menu-toggle');
  if (toggle) {
    const collapsed = document.querySelector('.shell').classList.toggle('collapsed');
    toggle.setAttribute('aria-expanded', String(!collapsed));
  }
});
document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('nav a').forEach(function (link) {
    if (new URL(link.href).pathname === window.location.pathname) link.setAttribute('aria-current', 'page');
  });
  // Include Spring's current CSRF token on Wicket's Ajax form-component requests.
  if (window.Wicket) Wicket.Event.subscribe('/ajax/call/beforeSend', function (event, attributes, xhr) {
    const token = document.querySelector('input[name="_csrf"]');
    if (token) xhr.setRequestHeader('X-CSRF-TOKEN', token.value);
  });
});
