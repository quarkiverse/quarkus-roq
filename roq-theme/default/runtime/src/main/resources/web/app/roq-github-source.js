(function () {
  function createSvg(path, viewBox) {
    var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', viewBox);
    svg.setAttribute('width', '14');
    svg.setAttribute('height', '14');
    var p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    p.setAttribute('fill', 'currentColor');
    p.setAttribute('d', path);
    svg.appendChild(p);
    return svg;
  }

  var tagPath = 'M1 7.775V2.75C1 1.784 1.784 1 2.75 1h5.025c.464 0 .91.184 1.238.513l6.25 6.25a1.75 1.75 0 0 1 0 2.474l-5.026 5.026a1.75 1.75 0 0 1-2.474 0l-6.25-6.25A1.75 1.75 0 0 1 1 7.775m1.5 0c0 .066.026.13.073.177l6.25 6.25a.25.25 0 0 0 .354 0l5.025-5.025a.25.25 0 0 0 0-.354l-6.25-6.25a.25.25 0 0 0-.177-.073H2.75a.25.25 0 0 0-.25.25ZM6 5a1 1 0 1 1 0 2 1 1 0 0 1 0-2';
  var starPath = 'M8 .25a.75.75 0 0 1 .673.418l1.882 3.815 4.21.612a.75.75 0 0 1 .416 1.279l-3.046 2.97.719 4.192a.751.751 0 0 1-1.088.791L8 12.347l-3.766 1.98a.75.75 0 0 1-1.088-.79l.72-4.194L.818 6.374a.75.75 0 0 1 .416-1.28l4.21-.611L7.327.668A.75.75 0 0 1 8 .25';
  var forkPath = 'M5 5.372v.878c0 .414.336.75.75.75h4.5a.75.75 0 0 0 .75-.75v-.878a2.25 2.25 0 1 1 1.5 0v.878a2.25 2.25 0 0 1-2.25 2.25h-1.5v2.128a2.251 2.251 0 1 1-1.5 0V8.5h-1.5A2.25 2.25 0 0 1 3.5 6.25v-.878a2.25 2.25 0 1 1 1.5 0M5 3.25a.75.75 0 1 0-1.5 0 .75.75 0 0 0 1.5 0m6.75.75a.75.75 0 1 0 0-1.5.75.75 0 0 0 0 1.5m-3 8.75a.75.75 0 1 0-1.5 0 .75.75 0 0 0 1.5 0';

  document.querySelectorAll('[data-github-source]').forEach(function (el) {
    var href = el.getAttribute('href');
    var match = href && href.match(/github\.com\/([^/]+)\/([^/]+)/);
    if (!match) return;

    var owner = match[1];
    var repo = match[2];
    var cacheKey = '__github_source.' + owner + '/' + repo;
    var cached = sessionStorage.getItem(cacheKey);

    if (cached) {
      render(el, JSON.parse(cached));
      return;
    }

    var api = 'https://api.github.com/repos/' + owner + '/' + repo;
    Promise.all([
      fetch(api).then(function (r) { return r.ok ? r.json() : null; }).catch(function () { return null; }),
      fetch(api + '/releases/latest').then(function (r) { return r.ok ? r.json() : null; }).catch(function () { return null; })
    ]).then(function (results) {
      var info = results[0];
      var release = results[1];
      if (!info) return;
      var data = { stars: info.stargazers_count, forks: info.forks_count };
      if (release && release.tag_name) data.version = release.tag_name;
      sessionStorage.setItem(cacheKey, JSON.stringify(data));
      render(el, data);
    });
  });

  function render(el, data) {
    var repoEl = el.querySelector('.github-source-repo');
    if (!repoEl) return;

    var facts = document.createElement('span');
    facts.className = 'github-source-facts';

    if (data.version) {
      var v = document.createElement('span');
      v.className = 'github-source-fact';
      v.appendChild(createSvg(tagPath, '0 0 16 16'));
      v.appendChild(document.createTextNode(data.version));
      facts.appendChild(v);
    }
    if (data.stars != null) {
      var s = document.createElement('span');
      s.className = 'github-source-fact';
      s.appendChild(createSvg(starPath, '0 0 16 16'));
      s.appendChild(document.createTextNode(data.stars));
      facts.appendChild(s);
    }
    if (data.forks != null) {
      var f = document.createElement('span');
      f.className = 'github-source-fact';
      f.appendChild(createSvg(forkPath, '0 0 16 16'));
      f.appendChild(document.createTextNode(data.forks));
      facts.appendChild(f);
    }

    repoEl.appendChild(facts);
    repoEl.classList.add('github-source-repo--active');
  }
})();
