import '@fontsource/inter/latin.css';
import Alpine from 'alpinejs';

window.Alpine = Alpine

Alpine.start()

// Anchors: clicking the link icon next to a section heading or a resume item copies the full URL
// (with the anchor) to the clipboard, without navigating (the address bar and scroll position are left untouched)
document.addEventListener('click', async (event) => {
    const link = event.target.closest('a.resume-anchor');
    if (!link) return;
    event.preventDefault();

    // Resolve against the current URL rather than <base href>, keeping path and query string
    const url = new URL(link.hash, location.href).href;

    try {
        await navigator.clipboard.writeText(url);
        link.dataset.copied = '';
        clearTimeout(link._copiedTimeout);
        link._copiedTimeout = setTimeout(() => delete link.dataset.copied, 1500);
    } catch (e) {
        // Clipboard API unavailable (non-secure context): expose the URL so it can be copied manually
        link.title = url;
    }
});

// When the page is opened with an anchor pointing to an item hidden behind "Show more", inside a
// collapsed item, or which is itself a collapsed item, reveal (and expand) it and scroll to it
function revealHashTarget() {
    if (!location.hash) return;
    const target = document.getElementById(decodeURIComponent(location.hash.slice(1)));
    if (!target) return;
    const ownDetails = target.querySelector(':scope > details');
    if (ownDetails) ownDetails.open = true;
    for (let el = target.parentElement; el; el = el.parentElement) {
        if (el.tagName === 'DETAILS') {
            el.open = true;
        }
        if (el.hasAttribute('x-data')) {
            const data = Alpine.$data(el);
            if ('showAll' in data) data.showAll = true;
        }
    }
    Alpine.nextTick(() => target.scrollIntoView());
}

revealHashTarget();
window.addEventListener('hashchange', revealHashTarget);
