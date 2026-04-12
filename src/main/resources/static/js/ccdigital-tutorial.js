(() => {
  const STORAGE_PREFIX = 'ccdigital.tour.seen.';
  const PANEL_GAP = 18;
  const VIEWPORT_MARGIN = 16;

  let tourUi = null;
  let activeTour = null;
  let repositionRaf = 0;

  function safeStorageKey(root) {
    const scope = (root.dataset.ccTutorialScope || root.id || 'general').trim();
    const rawKey = (root.dataset.ccTutorialKey || 'default').trim();
    return `${STORAGE_PREFIX}${scope}.${rawKey.replace(/\s+/g, '_')}`;
  }

  function hasSeenTour(root) {
    try {
      return window.localStorage.getItem(safeStorageKey(root)) === 'true';
    } catch (_) {
      return false;
    }
  }

  function markTourSeen(root) {
    try {
      window.localStorage.setItem(safeStorageKey(root), 'true');
    } catch (_) {
      // Si el navegador no permite storage, el tour sigue funcionando sin persistencia.
    }
  }

  function shouldAutoShow(root) {
    if (!root) return false;
    if (root.dataset.ccForceAutoshow === 'true') {
      return true;
    }
    return root.dataset.ccAutoshow === 'true' && !hasSeenTour(root);
  }

  function isVisible(element) {
    return !!element && element.getClientRects().length > 0 && window.getComputedStyle(element).visibility !== 'hidden';
  }

  function collectSteps(root) {
    const scope = (root.dataset.ccTutorialScope || '').trim();
    if (!scope) return [];

    return Array.from(document.querySelectorAll(`[data-cc-tour-scope="${scope}"][data-cc-tour-step]`))
      .filter(isVisible)
      .map((element) => ({
        element,
        order: Number(element.dataset.ccTourStep || '0'),
        title: (element.dataset.ccTourTitle || '').trim(),
        body: (element.dataset.ccTourBody || '').trim(),
        placement: (element.dataset.ccTourPlacement || 'bottom').trim().toLowerCase(),
      }))
      .sort((left, right) => left.order - right.order);
  }

  function ensureTourUi() {
    if (tourUi) return tourUi;

    const layer = document.createElement('div');
    layer.className = 'cc-tour-layer';
    layer.setAttribute('aria-hidden', 'true');
    layer.innerHTML = `
      <div class="cc-tour-backdrop"></div>
      <div class="cc-tour-spotlight"></div>
      <aside class="cc-tour-panel" role="dialog" aria-modal="true" aria-live="polite">
        <div class="cc-tour-badge">Tutorial guiado</div>
        <div class="cc-tour-counter"></div>
        <h2 class="cc-tour-title"></h2>
        <p class="cc-tour-text"></p>
        <div class="cc-tour-progress" aria-hidden="true"></div>
        <div class="cc-tour-actions">
          <button type="button" class="btn btn-outline-secondary btn-sm" data-cc-tour-cancel>Cancelar</button>
          <button type="button" class="btn btn-outline-secondary btn-sm" data-cc-tour-prev>Anterior</button>
          <button type="button" class="btn btn-outline-primary btn-sm" data-cc-tour-next>Siguiente</button>
          <button type="button" class="btn btn-primary btn-sm d-none" data-cc-tour-finish>Finalizar</button>
        </div>
        <button type="button" class="cc-tour-close" aria-label="Cerrar tutorial">
          <i class="bi bi-x-lg"></i>
        </button>
      </aside>
    `;

    document.body.appendChild(layer);

    tourUi = {
      layer,
      backdrop: layer.querySelector('.cc-tour-backdrop'),
      spotlight: layer.querySelector('.cc-tour-spotlight'),
      panel: layer.querySelector('.cc-tour-panel'),
      badge: layer.querySelector('.cc-tour-badge'),
      counter: layer.querySelector('.cc-tour-counter'),
      title: layer.querySelector('.cc-tour-title'),
      text: layer.querySelector('.cc-tour-text'),
      progress: layer.querySelector('.cc-tour-progress'),
      cancel: layer.querySelector('[data-cc-tour-cancel]'),
      prev: layer.querySelector('[data-cc-tour-prev]'),
      next: layer.querySelector('[data-cc-tour-next]'),
      finish: layer.querySelector('[data-cc-tour-finish]'),
      close: layer.querySelector('.cc-tour-close'),
    };

    tourUi.cancel.addEventListener('click', () => stopTour(true));
    tourUi.prev.addEventListener('click', () => moveStep(-1));
    tourUi.next.addEventListener('click', () => moveStep(1));
    tourUi.finish.addEventListener('click', () => stopTour(true));
    tourUi.close.addEventListener('click', () => stopTour(true));
    tourUi.backdrop.addEventListener('click', () => stopTour(true));

    window.addEventListener('resize', scheduleReposition);
    window.addEventListener('scroll', scheduleReposition, true);
    document.addEventListener('keydown', (event) => {
      if (!activeTour) return;
      if (event.key === 'Escape') {
        event.preventDefault();
        stopTour(true);
      } else if (event.key === 'ArrowRight') {
        event.preventDefault();
        moveStep(1);
      } else if (event.key === 'ArrowLeft') {
        event.preventDefault();
        moveStep(-1);
      }
    });

    return tourUi;
  }

  function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
  }

  function scheduleReposition() {
    if (!activeTour) return;
    if (repositionRaf) window.cancelAnimationFrame(repositionRaf);
    repositionRaf = window.requestAnimationFrame(() => {
      repositionRaf = 0;
      positionCurrentStep();
    });
  }

  function getPreferredPlacements(preferred) {
    const normalized = preferred === 'top' || preferred === 'left' || preferred === 'right' ? preferred : 'bottom';
    return [normalized, 'bottom', 'top', 'right', 'left'].filter((value, index, array) => array.indexOf(value) === index);
  }

  function computePanelPosition(targetRect, panelRect, preferred) {
    const placements = getPreferredPlacements(preferred);

    for (const placement of placements) {
      let top = 0;
      let left = 0;

      if (placement === 'top') {
        top = targetRect.top - panelRect.height - PANEL_GAP;
        left = targetRect.left;
      } else if (placement === 'left') {
        top = targetRect.top;
        left = targetRect.left - panelRect.width - PANEL_GAP;
      } else if (placement === 'right') {
        top = targetRect.top;
        left = targetRect.right + PANEL_GAP;
      } else {
        top = targetRect.bottom + PANEL_GAP;
        left = targetRect.left;
      }

      const fitsVertically = top >= VIEWPORT_MARGIN && top + panelRect.height <= window.innerHeight - VIEWPORT_MARGIN;
      const fitsHorizontally = left >= VIEWPORT_MARGIN && left + panelRect.width <= window.innerWidth - VIEWPORT_MARGIN;
      if (fitsVertically && fitsHorizontally) {
        return { top, left };
      }
    }

    return {
      top: clamp(targetRect.bottom + PANEL_GAP, VIEWPORT_MARGIN, window.innerHeight - panelRect.height - VIEWPORT_MARGIN),
      left: clamp(targetRect.left, VIEWPORT_MARGIN, window.innerWidth - panelRect.width - VIEWPORT_MARGIN),
    };
  }

  function renderProgress() {
    if (!activeTour || !tourUi) return;
    tourUi.progress.innerHTML = '';

    activeTour.steps.forEach((step, index) => {
      const dot = document.createElement('button');
      dot.type = 'button';
      dot.className = 'cc-tour-dot' + (index === activeTour.index ? ' is-active' : '');
      dot.setAttribute('aria-label', `Ir al paso ${index + 1}`);
      dot.addEventListener('click', () => showStep(index));
      tourUi.progress.appendChild(dot);
    });
  }

  function clearHighlight() {
    document.querySelectorAll('.cc-tour-target-active').forEach((element) => {
      element.classList.remove('cc-tour-target-active');
    });
  }

  function positionCurrentStep() {
    if (!activeTour || !tourUi) return;
    const currentStep = activeTour.steps[activeTour.index];
    if (!currentStep || !isVisible(currentStep.element)) return;

    const rect = currentStep.element.getBoundingClientRect();
    const padding = 10;
    const spotlightTop = clamp(rect.top - padding, 8, window.innerHeight - 8);
    const spotlightLeft = clamp(rect.left - padding, 8, window.innerWidth - 8);
    const spotlightWidth = clamp(rect.width + padding * 2, 44, window.innerWidth - spotlightLeft - 8);
    const spotlightHeight = clamp(rect.height + padding * 2, 44, window.innerHeight - spotlightTop - 8);

    tourUi.spotlight.style.top = `${spotlightTop}px`;
    tourUi.spotlight.style.left = `${spotlightLeft}px`;
    tourUi.spotlight.style.width = `${spotlightWidth}px`;
    tourUi.spotlight.style.height = `${spotlightHeight}px`;

    const panelRect = tourUi.panel.getBoundingClientRect();
    const panelPosition = computePanelPosition(rect, panelRect, currentStep.placement);
    tourUi.panel.style.top = `${panelPosition.top}px`;
    tourUi.panel.style.left = `${panelPosition.left}px`;
  }

  function showStep(index) {
    if (!activeTour || !tourUi) return;
    if (index < 0 || index >= activeTour.steps.length) return;

    activeTour.index = index;
    const currentStep = activeTour.steps[index];
    clearHighlight();
    currentStep.element.classList.add('cc-tour-target-active');

    currentStep.element.scrollIntoView({
      behavior: 'smooth',
      block: 'center',
      inline: 'center',
    });

    tourUi.badge.textContent = activeTour.root.dataset.ccTutorialScope === 'user'
      ? 'Tutorial de usuario'
      : activeTour.root.dataset.ccTutorialScope === 'admin'
        ? 'Tutorial de administración'
        : 'Tutorial del emisor';
    tourUi.counter.textContent = `Paso ${index + 1} de ${activeTour.steps.length}`;
    tourUi.title.textContent = currentStep.title || 'Paso del tutorial';
    tourUi.text.textContent = currentStep.body || '';
    tourUi.prev.disabled = index === 0;
    tourUi.next.classList.toggle('d-none', index === activeTour.steps.length - 1);
    tourUi.finish.classList.toggle('d-none', index !== activeTour.steps.length - 1);
    renderProgress();

    window.setTimeout(positionCurrentStep, 180);
  }

  function startTour(root, automatic) {
    const steps = collectSteps(root);
    if (!steps.length) return;

    if (activeTour) {
      stopTour(false);
    }

    ensureTourUi();
    activeTour = {
      root,
      steps,
      index: 0,
      automatic: automatic === true,
    };

    tourUi.layer.classList.add('is-active');
    tourUi.layer.setAttribute('aria-hidden', 'false');
    document.body.classList.add('cc-tour-open');
    showStep(0);
  }

  function stopTour(markSeen) {
    if (!activeTour || !tourUi) return;

    if (markSeen && activeTour.automatic) {
      markTourSeen(activeTour.root);
    }

    clearHighlight();
    tourUi.layer.classList.remove('is-active');
    tourUi.layer.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('cc-tour-open');
    activeTour = null;
  }

  function moveStep(direction) {
    if (!activeTour) return;
    const nextIndex = activeTour.index + direction;
    if (nextIndex < 0 || nextIndex >= activeTour.steps.length) return;
    showStep(nextIndex);
  }

  function bindRoot(root) {
    if (!root || root.dataset.ccTutorialBound === 'true') return;
    root.dataset.ccTutorialBound = 'true';

    document.querySelectorAll(`[data-cc-open-tutorial="${root.id}"]`).forEach((trigger) => {
      if (trigger.dataset.ccTutorialTriggerBound === 'true') return;
      trigger.dataset.ccTutorialTriggerBound = 'true';
      trigger.addEventListener('click', (event) => {
        event.preventDefault();
        startTour(root, false);
      });
    });

    if (shouldAutoShow(root)) {
      window.setTimeout(() => startTour(root, true), 500);
    }
  }

  function initTours() {
    document.querySelectorAll('[data-cc-tutorial-root]').forEach(bindRoot);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initTours);
  } else {
    initTours();
  }
})();
