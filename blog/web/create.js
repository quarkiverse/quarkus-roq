(function () {
  const CODE_QUARKUS = 'https://code.quarkus.io';
  const GITHUB_AUTH = 'https://github.com/login/oauth/authorize';
  const ROQ_EXT = 'io.quarkiverse.roq:quarkus-roq';

  const state = {
    title: 'My Roq Site',
    name: 'my-roq-site',
    nameEdited: false,
    theme: 'default',
    plugins: ['markdown'],
    githubClientId: null,
  };

  function slugify(text) {
    return text.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  }

  function extensionIds() {
    const el = document.getElementById('roq-extensions');
    if (!el) return [ROQ_EXT];
    const data = JSON.parse(el.textContent);
    const ids = [ROQ_EXT];
    const theme = data.find(e => e.kind === 'theme' && e.installName === state.theme);
    if (theme?.extensionId) ids.push(theme.extensionId);
    for (const name of state.plugins) {
      const p = data.find(e => e.kind === 'plugin' && e.installName === name);
      if (p?.extensionId) ids.push(p.extensionId);
    }
    return ids;
  }

  function projectParams(extra) {
    const params = new URLSearchParams();
    params.set('a', state.name);
    params.set('ndf', 'true');
    params.set('nw', 'true');
    params.append('ec', 'roq-project-codestart');
    params.append('cd', `site.title=${state.title}`);
    for (const id of extensionIds()) params.append('e', id);
    if (extra) for (const [k, v] of Object.entries(extra)) params.set(k, v);
    return params;
  }

  function updateCheckIcon(card, show) {
    const check = card.querySelector('.create-card-check');
    check.replaceChildren();
    if (show) {
      const i = document.createElement('i');
      i.className = 'fa-solid fa-check';
      check.appendChild(i);
    }
  }

  function init() {
    const root = document.querySelector('.create-form');
    if (!root) return;

    root.querySelector('[data-field="title"]').addEventListener('input', function () {
      state.title = this.value;
      if (!state.nameEdited) {
        state.name = slugify(state.title) || 'my-roq-site';
        root.querySelector('[data-field="name"]').value = state.name;
      }
    });

    root.querySelector('[data-field="name"]').addEventListener('input', function () {
      state.name = this.value;
      state.nameEdited = true;
    });

    root.querySelectorAll('[data-theme]').forEach(function (card) {
      card.addEventListener('click', function () {
        state.theme = this.dataset.theme;
        root.querySelectorAll('[data-theme]').forEach(function (c) {
          const selected = c.dataset.theme === state.theme;
          c.classList.toggle('selected', selected);
          updateCheckIcon(c, selected);
        });
      });
    });

    root.querySelectorAll('[data-plugin]').forEach(function (chip) {
      chip.addEventListener('click', function () {
        const name = this.dataset.plugin;
        if (name === 'markdown') return;
        const idx = state.plugins.indexOf(name);
        if (idx >= 0) state.plugins.splice(idx, 1);
        else state.plugins.push(name);
        this.classList.toggle('selected', state.plugins.includes(name));
      });
    });

    root.querySelector('[data-action="download"]').addEventListener('click', function () {
      window.open(CODE_QUARKUS + '/d?' + projectParams({ cn: 'roq' }), '_blank');
    });

    root.querySelector('[data-action="github"]').addEventListener('click', async function () {
      if (!state.githubClientId) {
        const res = await fetch(CODE_QUARKUS + '/api/config');
        state.githubClientId = (await res.json()).gitHubClientId;
      }
      if (!state.githubClientId) return;
      const redirectUri = CODE_QUARKUS + '/?' + projectParams({ github: 'true' });
      const auth = new URLSearchParams({
        client_id: state.githubClientId,
        redirect_uri: redirectUri,
        scope: 'public_repo,workflow',
        state: Math.random().toString(36).substring(2),
      });
      window.open(GITHUB_AUTH + '?' + auth, '_blank');
    });
  }

  document.addEventListener('DOMContentLoaded', init);
})();
