(() => {
  const STORAGE_KEY = 'ccdigital-theme';
  const LIGHT = 'light';
  const DARK = 'dark';

  function normalizeTheme(theme) {
    return theme === DARK ? DARK : LIGHT;
  }

  function readStoredTheme() {
    try {
      const stored = window.localStorage.getItem(STORAGE_KEY);
      return stored === LIGHT || stored === DARK ? stored : null;
    } catch (_) {
      return null;
    }
  }

  function resolveTheme() {
    const stored = readStoredTheme();
    if (stored) return stored;
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
      return DARK;
    }
    return LIGHT;
  }

  function applyTheme(theme) {
    const resolved = normalizeTheme(theme);
    document.documentElement.setAttribute('data-bs-theme', resolved);
    document.documentElement.setAttribute('data-cc-theme', resolved);
    if (document.body) {
      document.body.setAttribute('data-cc-theme', resolved);
    }
    updateToggleButtons(resolved);
    return resolved;
  }

  function persistTheme(theme) {
    try {
      window.localStorage.setItem(STORAGE_KEY, normalizeTheme(theme));
    } catch (_) {
      // Si el navegador bloquea localStorage, el cambio se conserva solo durante la sesión actual.
    }
  }

  function getCurrentTheme() {
    return normalizeTheme(document.documentElement.getAttribute('data-bs-theme'));
  }

  function updateToggleButtons(theme) {
    const resolved = normalizeTheme(theme);
    document.querySelectorAll('[data-cc-theme-toggle]').forEach((button) => {
      const isDark = resolved === DARK;
      const icon = button.querySelector('[data-cc-theme-icon]');
      const label = button.querySelector('[data-cc-theme-label]');
      const srOnly = button.querySelector('[data-cc-theme-sr]');
      if (icon) {
        icon.className = `bi ${isDark ? 'bi-sun-fill' : 'bi-moon-stars-fill'}`;
      }
      if (label) {
        label.textContent = isDark ? 'Modo claro' : 'Modo oscuro';
      }
      if (srOnly) {
        srOnly.textContent = isDark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro';
      }
      button.setAttribute('aria-pressed', isDark ? 'true' : 'false');
      button.setAttribute('title', isDark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro');
    });
  }

  function toggleTheme() {
    const nextTheme = getCurrentTheme() === DARK ? LIGHT : DARK;
    persistTheme(nextTheme);
    applyTheme(nextTheme);
  }

  function ensureActionContainer(navbar) {
    const container = navbar.querySelector('.container');
    if (!container) return navbar;

    container.classList.add('d-flex', 'justify-content-between', 'align-items-center');

    const directChildren = Array.from(container.children);
    if (directChildren.length <= 1) {
      const wrapper = document.createElement('div');
      wrapper.className = 'd-flex align-items-center gap-2 flex-wrap justify-content-end';
      container.appendChild(wrapper);
      return wrapper;
    }

    const existingWrapper = directChildren
      .slice(1)
      .find((child) => child.classList && child.classList.contains('d-flex'));

    if (existingWrapper) return existingWrapper;

    const wrapper = document.createElement('div');
    wrapper.className = 'd-flex align-items-center gap-2 flex-wrap justify-content-end';
    directChildren.slice(1).forEach((child) => wrapper.appendChild(child));
    container.appendChild(wrapper);
    return wrapper;
  }

  function createToggleButton() {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'btn btn-sm btn-outline-secondary cc-theme-toggle';
    button.setAttribute('data-cc-theme-toggle', 'true');
    button.setAttribute('aria-live', 'polite');
    button.innerHTML = `
      <i class="bi bi-moon-stars-fill" data-cc-theme-icon aria-hidden="true"></i>
      <span class="cc-theme-toggle-label d-none d-sm-inline" data-cc-theme-label>Modo oscuro</span>
      <span class="visually-hidden" data-cc-theme-sr>Cambiar a modo oscuro</span>
    `;
    button.addEventListener('click', toggleTheme);
    return button;
  }

  function attachToggle(navbar) {
    if (navbar.dataset.ccThemeReady === 'true') return;
    navbar.dataset.ccThemeReady = 'true';

    const actions = ensureActionContainer(navbar);
    const button = createToggleButton();
    const tutorialButton = actions.querySelector('[data-cc-open-tutorial]');
    if (tutorialButton) {
      actions.insertBefore(button, tutorialButton);
    } else {
      actions.appendChild(button);
    }
    updateToggleButtons(getCurrentTheme());
  }

  function attachAllToggles() {
    document.querySelectorAll('.cc-navbar, .cc-landing-nav').forEach(attachToggle);
  }

  applyTheme(resolveTheme());

  const darkModeMedia = window.matchMedia
    ? window.matchMedia('(prefers-color-scheme: dark)')
    : null;

  if (darkModeMedia) {
    const syncWithSystem = (event) => {
      if (readStoredTheme()) return;
      applyTheme(event.matches ? DARK : LIGHT);
    };

    if (typeof darkModeMedia.addEventListener === 'function') {
      darkModeMedia.addEventListener('change', syncWithSystem);
    } else if (typeof darkModeMedia.addListener === 'function') {
      darkModeMedia.addListener(syncWithSystem);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', attachAllToggles);
  } else {
    attachAllToggles();
  }

  window.CCDigitalTheme = {
    applyTheme,
    attachAllToggles,
    getTheme: getCurrentTheme,
    toggleTheme,
  };
})();
