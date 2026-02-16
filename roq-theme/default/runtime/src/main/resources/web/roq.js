// Import Tailwind CSS
import './roq.css';

// Dark Mode Toggle
(function() {
    function updateIcon(isDark) {
        const icon = document.querySelector('.dark-mode-toggle i');
        if (icon) {
            if (isDark) {
                icon.classList.remove('fa-moon');
                icon.classList.add('fa-sun');
            } else {
                icon.classList.remove('fa-sun');
                icon.classList.add('fa-moon');
            }
        }
    }

    function updateGiscusTheme(isDark) {
        const iframe = document.querySelector('iframe.giscus-frame');
        if (iframe) {
            iframe.contentWindow.postMessage(
                { giscus: { setConfig: { theme: isDark ? 'dark' : 'light' } } },
                'https://giscus.app'
            );
        }
    }

    function toggleDarkMode() {
        const html = document.documentElement;
        html.classList.toggle('dark');
        const isDark = html.classList.contains('dark');
        localStorage.setItem('darkMode', isDark);
        updateIcon(isDark);
        updateGiscusTheme(isDark);
    }

    // Wait for DOM to be ready
    document.addEventListener('DOMContentLoaded', function() {
        // Set initial icon state
        const isDark = document.documentElement.classList.contains('dark');
        updateIcon(isDark);

        // Add click handler to dark mode toggle
        const toggle = document.querySelector('.dark-mode-toggle');
        if (toggle) {
            toggle.addEventListener('click', toggleDarkMode);
        }
    });
})();