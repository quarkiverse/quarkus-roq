import '@fontsource/inter/latin.css';
import Alpine from 'alpinejs';

window.Alpine = Alpine

Alpine.start()

// Section anchors: clicking the link icon next to a section heading copies the full URL (with the anchor)
// to the clipboard, without navigating (the address bar and scroll position are left untouched)
document.addEventListener('click', async (event) => {
    const link = event.target.closest('a.section-anchor');
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
