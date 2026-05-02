// Marketplace filtering and search
(function () {
  const grid = document.getElementById('marketplace-grid');
  if (!grid) return;

  const cards = Array.from(grid.querySelectorAll('.marketplace-card'));
  const searchInput = document.getElementById('marketplace-search');
  const typeChips = document.querySelectorAll('.marketplace-chip[data-filter-type]');
  const tagsContainer = document.getElementById('marketplace-tags');
  const emptyState = document.getElementById('marketplace-empty');

  let activeType = 'all';
  let activeTag = null;

  // Build tag chips from card data
  const allTags = new Set();
  cards.forEach(card => {
    const tags = card.dataset.tags;
    if (tags) tags.split(' ').forEach(t => allTags.add(t));
  });

  Array.from(allTags).sort().forEach(tag => {
    const chip = document.createElement('button');
    chip.className = 'marketplace-tag-chip';
    chip.dataset.filterTag = tag;
    chip.textContent = tag;
    chip.addEventListener('click', () => {
      if (activeTag === tag) {
        activeTag = null;
        chip.classList.remove('active');
      } else {
        tagsContainer.querySelectorAll('.marketplace-tag-chip').forEach(c => c.classList.remove('active'));
        activeTag = tag;
        chip.classList.add('active');
      }
      applyFilters();
    });
    tagsContainer.appendChild(chip);
  });

  // Type chip clicks
  typeChips.forEach(chip => {
    chip.addEventListener('click', () => {
      typeChips.forEach(c => c.classList.remove('active'));
      chip.classList.add('active');
      activeType = chip.dataset.filterType;
      // Update URL fragment
      if (activeType === 'all') {
        history.replaceState(null, '', window.location.pathname);
      } else {
        history.replaceState(null, '', '#' + activeType + 's');
      }
      applyFilters();
    });
  });

  // Auto-select type from URL fragment (#plugins, #themes)
  const fragment = window.location.hash.replace('#', '').replace(/s$/, '');
  if (fragment) {
    const matchingChip = Array.from(typeChips).find(c => c.dataset.filterType === fragment);
    if (matchingChip) {
      matchingChip.click();
    }
  }

  // Search input
  if (searchInput) {
    searchInput.addEventListener('input', () => applyFilters());
  }

  function applyFilters() {
    const query = (searchInput ? searchInput.value : '').toLowerCase().trim();
    let visible = 0;

    cards.forEach(card => {
      const type = card.dataset.type;
      const tags = card.dataset.tags || '';
      const title = card.querySelector('.marketplace-card-title')?.textContent.toLowerCase() || '';
      const desc = card.querySelector('.marketplace-card-desc')?.textContent.toLowerCase() || '';

      const matchesType = activeType === 'all' || type === activeType;
      const matchesTag = !activeTag || tags.split(' ').includes(activeTag);
      const matchesSearch = !query || title.includes(query) || desc.includes(query) || tags.includes(query);

      if (matchesType && matchesTag && matchesSearch) {
        card.classList.remove('hidden');
        visible++;
      } else {
        card.classList.add('hidden');
      }
    });

    if (emptyState) {
      emptyState.style.display = visible === 0 ? '' : 'none';
    }
  }
})();