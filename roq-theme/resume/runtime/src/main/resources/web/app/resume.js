import '@fontsource/inter/latin.css';
import Alpine from 'alpinejs';

window.Alpine = Alpine

Alpine.start()

// Section anchors: clicking the link icon next to a section heading copies the full URL (with the anchor)
document.addEventListener('click', async (event) => {
    const link = event.target.closest('a.section-anchor');
    if (!link) return;
    event.preventDefault();

    const hash = link.hash;
    // Resolve against the current URL rather than <base href>, keeping path and query string
    const url = new URL(hash, location.href).href;

    history.replaceState(null, '', url);
    document.getElementById(decodeURIComponent(hash.slice(1)))?.scrollIntoView({ behavior: 'smooth' });

    try {
        await navigator.clipboard.writeText(url);
        link.dataset.copied = '';
        clearTimeout(link._copiedTimeout);
        link._copiedTimeout = setTimeout(() => delete link.dataset.copied, 1500);
    } catch (e) {
        // Clipboard API unavailable (non-secure context): the URL is still updated in the address bar
    }
});
