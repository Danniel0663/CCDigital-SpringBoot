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

  function bindActionClicks(selector = 'a[data-cc-loader], button[data-cc-loader]') {
    document.querySelectorAll(selector).forEach((actionEl) => {
      if (actionEl.dataset.ccLoaderActionBound === 'true') return;
      actionEl.dataset.ccLoaderActionBound = 'true';

      // Soporta loader en acciones de navegación (links/botones) además de formularios.
      actionEl.addEventListener('click', (event) => {
        if (actionEl.disabled) return;

        if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button !== 0) {
          return;
        }

        if (actionEl.tagName === 'A') {
          const href = actionEl.getAttribute('href') || '';
          const target = actionEl.getAttribute('target') || '';
          if (!href || href === '#' || href.startsWith('javascript:') || target === '_blank') {
            return;
          }
        }

        const msg = actionEl.dataset.ccLoaderMessage || 'Procesando solicitud...';
        if (actionEl.tagName === 'BUTTON') {
          setButtonBusy(
            actionEl,
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

  function resolveIdleSessionConfig() {
    const path = window.location.pathname || '';
    if (path === '/user' || path.startsWith('/user/')) {
      return {
        keepaliveUrl: '/user/session/keepalive',
        expireUrl: '/user/session/expire',
        loginUrl: '/login/user?expired=true',
      };
    }
    if (path === '/issuer' || path.startsWith('/issuer/')) {
      return {
        keepaliveUrl: '/issuer/session/keepalive',
        expireUrl: '/issuer/session/expire',
        loginUrl: '/login/issuer?expired=true',
      };
    }
    if (path === '/admin' || path.startsWith('/admin/')) {
      return {
        keepaliveUrl: '/admin/session/keepalive',
        expireUrl: '/admin/session/expire',
        loginUrl: '/login/admin?expired=true',
      };
    }
    return null;
  }

  function bindIdleSessionTimeout() {
    if (window.__ccIdleTimeoutBound === true) return;
    const config = resolveIdleSessionConfig();
    if (!config) return;
    window.__ccIdleTimeoutBound = true;

    // Timeout estricto por inactividad: solo se reinicia con actividad real del usuario.
    const IDLE_LIMIT_MS = 5 * 60 * 1000;
    const HEARTBEAT_INTERVAL_MS = 60 * 1000;
    const TICK_INTERVAL_MS = 15 * 1000;
    const THROTTLE_ACTIVITY_MS = 800;

    let lastActivityAt = Date.now();
    let lastHeartbeatAt = 0;
    let lastMouseMoveAt = 0;
    let expiring = false;

    const markActivity = () => {
      if (expiring) return;
      lastActivityAt = Date.now();
    };

    const markMouseMoveActivity = () => {
      const now = Date.now();
      if (now - lastMouseMoveAt < THROTTLE_ACTIVITY_MS) return;
      lastMouseMoveAt = now;
      markActivity();
    };

    const passiveOpts = { passive: true };
    window.addEventListener('mousedown', markActivity, passiveOpts);
    window.addEventListener('keydown', markActivity, passiveOpts);
    window.addEventListener('touchstart', markActivity, passiveOpts);
    window.addEventListener('scroll', markActivity, passiveOpts);
    window.addEventListener('wheel', markActivity, passiveOpts);
    window.addEventListener('mousemove', markMouseMoveActivity, passiveOpts);
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) markActivity();
    });

    const sendKeepalive = async () => {
      try {
        await fetch(config.keepaliveUrl, {
          method: 'GET',
          credentials: 'same-origin',
          cache: 'no-store',
          headers: { 'X-Requested-With': 'XMLHttpRequest' },
        });
      } catch (_) {
        // Si falla, no interrumpimos UI; el timeout del backend seguirá aplicando.
      }
    };

    const expireNow = async () => {
      if (expiring) return;
      expiring = true;
      show('Sesión expirada por inactividad. Redirigiendo al login...');

      try {
        await fetch(config.expireUrl, {
          method: 'GET',
          credentials: 'same-origin',
          cache: 'no-store',
          headers: { 'X-Requested-With': 'XMLHttpRequest' },
        });
      } catch (_) {
        // Si falla la invalidación explícita, se fuerza redirección a login expirado.
      } finally {
        window.location.href = config.loginUrl;
      }
    };

    const intervalId = window.setInterval(() => {
      if (expiring) return;
      const now = Date.now();
      const idleForMs = now - lastActivityAt;

      if (idleForMs >= IDLE_LIMIT_MS) {
        expireNow();
        return;
      }

      // Heartbeat periódico solo si hubo actividad reciente; no mantiene sesión en estado inactivo.
      if (now - lastHeartbeatAt >= HEARTBEAT_INTERVAL_MS && idleForMs < HEARTBEAT_INTERVAL_MS) {
        lastHeartbeatAt = now;
        sendKeepalive();
      }
    }, TICK_INTERVAL_MS);

    window.addEventListener('beforeunload', () => {
      window.clearInterval(intervalId);
    }, { once: true });
  }

  window.CCDigitalLoader = {
    show,
    hide,
    update,
    setButtonBusy,
    bindFormSubmits,
    bindActionClicks,
    bindProfileToggles,
    bindIdleSessionTimeout,
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      bindFormSubmits();
      bindActionClicks();
      bindProfileToggles();
      bindIdleSessionTimeout();
    });
  } else {
    bindFormSubmits();
    bindActionClicks();
    bindProfileToggles();
    bindIdleSessionTimeout();
  }
})();
