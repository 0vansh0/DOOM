(() => {
  const root = document.documentElement;
  const themeButton = document.querySelector('[data-theme-toggle]');
  themeButton?.addEventListener('click', () => {
    const light = root.dataset.theme !== 'light';
    root.dataset.theme = light ? 'light' : 'dark';
    const label = themeButton.querySelector('[data-theme-label]');
    const icon = themeButton.querySelector('[data-theme-icon]');
    if (label) label.textContent = light ? 'Dark' : 'Light';
    if (icon) icon.innerHTML = light
      ? '<path d="M16.8 12.7A7.2 7.2 0 0 1 7.3 3.2 7.3 7.3 0 1 0 16.8 12.7Z"/><path d="M13.5 2.5v2m-1-1h2"/>'
      : '<circle cx="10" cy="10" r="3.2"/><path d="M10 1.5v2M10 16.5v2M1.5 10h2m13 0h2M4 4l1.4 1.4m9.2 9.2L16 16M16 4l-1.4 1.4m-9.2 9.2L4 16"/>';
    themeButton.setAttribute('aria-label', `Switch to ${light ? 'dark' : 'light'} theme`);
  });

  const definitions = {
    'battery-guardian': {
      title: 'BATTERY GUARDIAN',
      summary: 'Declared · awaiting user approval',
      previewName: 'DOOM · BATTERY GUARDIAN',
      previewTitle: 'Low battery',
      previewMessage: 'Connect your charger',
      trigger: 'When battery drops to 20% or lower while unplugged',
      action: 'Show an in-app preview card: “Connect your charger”',
      reads: 'battery.level · battery.read',
      surface: 'In-app preview card',
      readable: [
        'when battery level is 20% or lower',
        'and the phone is not charging',
        'and the mod is enabled',
        'then show an in-app preview card',
        'with “Connect your charger”',
        'for the duration set by the user'
      ],
      charging: false
    },
    'battery-glow': {
      title: 'BATTERY GLOW',
      summary: 'Declared · awaiting user approval',
      previewName: 'DOOM · BATTERY GLOW',
      previewTitle: 'Charging started',
      previewMessage: 'Battery ring + floating card',
      trigger: 'When charging starts',
      action: 'Show a battery-ring overlay and floating card',
      reads: 'battery.charging · battery.read',
      surface: 'Battery-ring overlay · floating card',
      readable: [
        'when charging starts',
        'and the mod is enabled',
        'then show the battery-ring overlay',
        'and a floating card',
        'using the selected color and intensity',
        'for the duration set by the user'
      ],
      charging: true
    },
    'edge-aura': {
      title: 'EDGE AURA',
      summary: 'Declared · awaiting user approval',
      previewName: 'DOOM · EDGE AURA',
      previewTitle: 'Charging started',
      previewMessage: 'Styled battery ring + card',
      trigger: 'When charging starts',
      action: 'Show a styled battery-ring overlay and floating card',
      reads: 'battery.charging · battery.read',
      surface: 'Configurable battery-ring overlay · floating card',
      readable: [
        'when charging starts',
        'and the mod is enabled',
        'then show the battery-ring overlay',
        'and a floating card',
        'with user-selected ring styling',
        'for the duration set by the user'
      ],
      charging: true
    }
  };

  const sampleTabs = [...document.querySelectorAll('.sample-tab[data-mod]')];
  const readableCode = document.querySelector('#readable-view code');
  const jsonCode = document.querySelector('[data-json-definition]');
  const titleNode = document.querySelector('[data-mod-title]');
  const reviewSummary = document.querySelector('[data-review-summary]');
  const previewName = document.querySelector('[data-preview-name]');
  const previewTitle = document.querySelector('[data-preview-title]');
  const previewMessage = document.querySelector('[data-preview-message]');
  const phoneNotice = document.querySelector('[data-phone-notice]');
  const readablePanel = document.querySelector('[data-view-panel="readable"]');
  const readPanel = document.querySelector('[data-view-panel="readable"]');
  const jsonPanel = document.querySelector('[data-view-panel="json"]');
  const definitionViews = document.querySelector('[data-definition-views]');
  const denialToggle = document.querySelector('[data-denial-toggle]');
  const denialResult = document.querySelector('[data-denial-result]');
  let currentMod = 'battery-guardian';
  let definitionCache = new Map();
  let viewAnimationToken = 0;
  let previousBatteryActive = true;

  async function loadDefinition(id) {
    if (definitionCache.has(id)) return definitionCache.get(id);
    const response = await fetch(`examples/${id}.json`, { credentials: 'same-origin', redirect: 'error' });
    if (!response.ok) throw new Error('Definition is unavailable.');
    const data = await response.json();
    const formatted = JSON.stringify(data, null, 2);
    definitionCache.set(id, formatted);
    return formatted;
  }

  async function setJson(id) {
    if (!jsonCode) return;
    jsonCode.textContent = 'Loading checked-in definition…';
    try {
      jsonCode.textContent = await loadDefinition(id);
    } catch {
      jsonCode.textContent = 'Could not load this example definition. Try again when the local preview is available.';
    }
  }

  function updateBattery(level) {
    const output = document.querySelector('#battery-value');
    const sim = document.querySelector('[data-battery-sim]');
    const active = currentMod !== 'battery-guardian' || Number(level) <= 20;
    if (output) output.value = `${level}%`;
    sim?.classList.toggle('is-triggered', active);
    readablePanel?.classList.toggle('is-triggered', active);
    phoneNotice?.classList.toggle('is-triggered', active);
    if (active !== previousBatteryActive) {
      const threshold = document.querySelector('[data-threshold]');
      threshold?.classList.remove('is-pulsed');
      if (active && threshold) {
        void threshold.offsetWidth;
        threshold.classList.add('is-pulsed');
      }
    }
    previousBatteryActive = active;
  }

  function selectMod(id) {
    const item = definitions[id];
    if (!item) return;
    currentMod = id;
    sampleTabs.forEach((tab) => {
      const selected = tab.dataset.mod === id;
      tab.classList.toggle('is-active', selected);
      tab.setAttribute('aria-selected', String(selected));
      tab.tabIndex = selected ? 0 : -1;
    });
    const selectedIndex = sampleTabs.findIndex((tab) => tab.dataset.mod === id);
    document.querySelector('.sample-tabs')?.style.setProperty('--active-index', String(selectedIndex));
    document.querySelectorAll('.mod-card[data-mod]').forEach((card) => {
      card.setAttribute('aria-pressed', String(card.dataset.mod === id));
    });
    if (titleNode) titleNode.textContent = item.title;
    if (reviewSummary) reviewSummary.textContent = item.summary;
    const readsNode = document.querySelector('[data-capabilities]');
    if (readsNode) readsNode.textContent = item.reads;
    const surfaceNode = document.querySelector('[data-surface]');
    if (surfaceNode) surfaceNode.textContent = item.surface;
    if (previewName) previewName.textContent = item.previewName;
    if (previewTitle) previewTitle.textContent = item.previewTitle;
    if (previewMessage) previewMessage.textContent = item.previewMessage;
    if (readableCode) readableCode.innerHTML = item.readable.map((line, index) => {
      const condition = index < (id === 'battery-guardian' ? 3 : 2);
      const escaped = line.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
      const formatted = id === 'battery-guardian'
        ? escaped.replace('20%', '<mark data-threshold>20%</mark>').replace('Connect your charger', '<mark>Connect your charger</mark>')
        : escaped;
      return `<span class="code-line"${condition ? ' data-condition-line' : ''}>${formatted}</span>${index === item.readable.length - 1 ? '' : '\n'}`;
    }).join('');
    readableCode?.closest('pre')?.setAttribute('aria-label', `Readable ${item.title} rule`);
    document.querySelector('[data-battery-sim]')?.toggleAttribute('hidden', id !== 'battery-guardian');
    document.querySelector('.demo-shell')?.classList.remove('is-triggered');
    previousBatteryActive = ! (id === 'battery-guardian');
    updateBattery(document.querySelector('#battery-level')?.value ?? 18);
    if (jsonPanel && !jsonPanel.hidden) setJson(id);
  }

  sampleTabs.forEach((tab, index) => {
    tab.tabIndex = index === 0 ? 0 : -1;
    tab.addEventListener('click', () => selectMod(tab.dataset.mod));
    tab.addEventListener('keydown', (event) => {
      if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
      event.preventDefault();
      const next = event.key === 'Home' ? 0 : event.key === 'End' ? sampleTabs.length - 1
        : (index + (event.key === 'ArrowRight' ? 1 : sampleTabs.length - 1)) % sampleTabs.length;
      sampleTabs[next].focus();
      selectMod(sampleTabs[next].dataset.mod);
    });
  });

  document.querySelectorAll('.mod-card[data-mod]').forEach((card) => {
    card.addEventListener('click', () => {
      selectMod(card.dataset.mod);
      const behavior = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth';
      document.querySelector('#sandbox')?.scrollIntoView({ behavior, block: 'start' });
    });
  });

  document.querySelectorAll('[data-view-tab]').forEach((button) => button.addEventListener('click', async () => {
    if (button.getAttribute('aria-pressed') === 'true') return;
    const showJson = button.dataset.viewTab === 'json';
    if (showJson) await setJson(currentMod);
    const nextPanel = showJson ? jsonPanel : readPanel;
    const previousPanel = showJson ? readPanel : jsonPanel;
    if (!nextPanel || !previousPanel || nextPanel === previousPanel || !definitionViews) return;
    const token = ++viewAnimationToken;
    nextPanel.hidden = false;
    definitionViews.style.height = `${previousPanel.getBoundingClientRect().height}px`;
    const nextHeight = nextPanel.getBoundingClientRect().height;
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduceMotion) {
      previousPanel.hidden = true;
      definitionViews.style.height = '';
    } else {
      const heightAnimation = definitionViews.animate(
        [{ height: `${previousPanel.getBoundingClientRect().height}px` }, { height: `${nextHeight}px` }],
        { duration: 140, easing: 'ease-out' }
      );
      previousPanel.animate([{ opacity: 1, transform: 'translateY(0)' }, { opacity: 0, transform: 'translateY(-4px)' }], { duration: 140, easing: 'ease-out' });
      nextPanel.animate([{ opacity: 0, transform: 'translateY(4px)' }, { opacity: 1, transform: 'translateY(0)' }], { duration: 140, easing: 'ease-out' });
      await heightAnimation.finished.catch(() => {});
      if (token !== viewAnimationToken) return;
      previousPanel.hidden = true;
      definitionViews.style.height = '';
    }
    document.querySelectorAll('[data-view-tab]').forEach((tab) => {
      const selected = tab === button;
      tab.classList.toggle('is-active', selected);
      tab.setAttribute('aria-pressed', String(selected));
    });
    document.querySelector('.view-tabs')?.style.setProperty('--active-index', showJson ? '1' : '0');
  }));

  denialToggle?.addEventListener('click', () => {
    const active = denialToggle.getAttribute('aria-pressed') !== 'true';
    denialToggle.setAttribute('aria-pressed', String(active));
    denialResult?.toggleAttribute('hidden', !active);
  });

  document.querySelector('#battery-level')?.addEventListener('input', (event) => updateBattery(event.target.value));

  document.querySelectorAll('[data-copy]').forEach((button) => button.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(button.dataset.copy);
      button.classList.add('is-copied');
      button.setAttribute('aria-label', 'Copied to clipboard');
      window.setTimeout(() => {
        button.classList.remove('is-copied');
        button.setAttribute('aria-label', 'Copy command');
      }, 1500);
    } catch {
      button.classList.add('copy-failed');
      button.setAttribute('aria-label', 'Clipboard unavailable; select and copy the command');
      window.setTimeout(() => {
        button.classList.remove('copy-failed');
        button.setAttribute('aria-label', 'Copy command');
      }, 1500);
    }
  }));

  selectMod(currentMod);
})();
