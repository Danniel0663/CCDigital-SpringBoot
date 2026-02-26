(() => {
  let overlayEl = null;
  let textEl = null;
  let activeCount = 0;

  function ensureOverlay() {
    if (overlayEl) return;

    overlayEl = document.createElement('div');
    overlayEl.id = 'ccGlobalLoader';
    overlayEl.className = 'cc-loader-overlay';
    overlayEl.setAttribute('aria-hidden', 'true');
    overlayEl.innerHTML = `
      <div class="cc-loader-card" role="status" aria-live="polite" aria-busy="true">
        <div class="cc-loader-symbol" aria-hidden="true">
          <div class="cc-loader-badge">
            <i class="bi bi-person-vcard-fill"></i>
            <span class="cc-loader-shield"><i class="bi bi-shield-check"></i></span>
          </div>
        </div>
        <div class="cc-loader-title">CCDigital</div>
        <div class="cc-loader-text">Procesando...</div>
      </div>
    `;
    document.body.appendChild(overlayEl);
    textEl = overlayEl.querySelector('.cc-loader-text');
  }

  function show(message) {
    ensureOverlay();
    activeCount++;
    if (textEl && typeof message === 'string' && message.trim()) {
      textEl.textContent = message.trim();
    }
    overlayEl.classList.add('is-visible');
    overlayEl.setAttribute('aria-hidden', 'false');
  }

  function hide(force) {
    if (!overlayEl) return;
    if (force === true) {
      activeCount = 0;
    } else {
      activeCount = Math.max(0, activeCount - 1);
    }
    if (activeCount > 0) return;
    overlayEl.classList.remove('is-visible');
    overlayEl.setAttribute('aria-hidden', 'true');
  }

  function update(message) {
    ensureOverlay();
    if (textEl && typeof message === 'string' && message.trim()) {
      textEl.textContent = message.trim();
    }
  }

  function setButtonBusy(button, busy, busyHtml, idleHtml) {
    if (!button) return;

    if (busy) {
      if (!button.dataset.ccLoaderOriginalHtml) {
        button.dataset.ccLoaderOriginalHtml = button.innerHTML;
      }
      button.disabled = true;
      button.classList.add('cc-btn-busy');
      if (busyHtml) {
        button.innerHTML = busyHtml;
      }
      return;
    }

    button.disabled = false;
    button.classList.remove('cc-btn-busy');
    if (typeof idleHtml === 'string') {
      button.innerHTML = idleHtml;
      return;
    }
    if (button.dataset.ccLoaderOriginalHtml) {
      button.innerHTML = button.dataset.ccLoaderOriginalHtml;
      delete button.dataset.ccLoaderOriginalHtml;
    }
  }

  function bindFormSubmits(selector = 'form[data-cc-loader]') {
    document.querySelectorAll(selector).forEach((form) => {
      if (form.dataset.ccLoaderBound === 'true') return;
      form.dataset.ccLoaderBound = 'true';

      form.addEventListener('submit', () => {
        const msg = form.dataset.ccLoaderMessage || 'Procesando solicitud...';
        const submit = form.querySelector('button[type="submit"], input[type="submit"]');
        if (submit) {
          setButtonBusy(
            submit,
            true,
            '<span class="spinner-border spinner-border-sm me-2"></span>Procesando...'
          );
        }
        show(msg);
      });
    });
  }

  function bindProfileToggles(selector = '[data-cc-profile-toggle], [data-cc-panel-toggle]') {
    document.querySelectorAll(selector).forEach((trigger) => {
      if (trigger.dataset.ccProfileToggleBound === 'true') return;
      trigger.dataset.ccProfileToggleBound = 'true';

      // Soporta tanto chips de perfil como botones de panel (notificaciones)
      // usando un mismo comportamiento de abrir/cerrar panel + estado visual activo.
      const targetId = trigger.dataset.ccProfileToggle || trigger.dataset.ccPanelToggle;
      if (!targetId) return;

      const panel = document.getElementById(targetId);
      if (!panel) return;

      trigger.setAttribute('role', 'button');
      trigger.setAttribute('tabindex', trigger.getAttribute('tabindex') || '0');
      trigger.setAttribute('aria-controls', targetId);
      trigger.setAttribute('aria-expanded', panel.classList.contains('d-none') ? 'false' : 'true');

      const toggle = () => {
        const willOpen = panel.classList.contains('d-none');
        panel.classList.toggle('d-none', !willOpen);
        // La clase is-open activa el resaltado visual del trigger en navbar.
        trigger.classList.toggle('is-open', willOpen);
        trigger.setAttribute('aria-expanded', willOpen ? 'true' : 'false');
        if (willOpen) {
          try {
            panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
          } catch (_) {
            panel.scrollIntoView(true);
          }
        }
      };

      trigger.addEventListener('click', toggle);
      trigger.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          toggle();
        }
      });
    });
  }

  window.CCDigitalLoader = {
    show,
    hide,
    update,
    setButtonBusy,
    bindFormSubmits,
    bindProfileToggles,
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      bindFormSubmits();
      bindProfileToggles();
    });
  } else {
    bindFormSubmits();
    bindProfileToggles();
  }
})();
