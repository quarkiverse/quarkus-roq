(function() {
    'use strict';
    function updateThemeIcons(theme) {
        const sunIcons = document.querySelectorAll('.toggle-light');
        const moonIcons = document.querySelectorAll('.toggle-dark');
        const themeTexts = document.querySelectorAll('.theme-text');

        const isDark = theme === 'dark';

        sunIcons.forEach(icon => icon.classList.toggle('hidden', !isDark));
        moonIcons.forEach(icon => icon.classList.toggle('hidden', isDark));
        themeTexts.forEach(text => text.textContent = isDark ? 'LIGHT MODE' : 'DARK MODE');
    }

    function setTheme(theme){
        if (theme === 'dark') {
            document.documentElement.classList.add('dark');
            localStorage.setItem('theme', 'dark');
        } else {
            document.documentElement.classList.remove('dark');
            localStorage.setItem('theme', 'light');
        }
        updateThemeIcons(theme);
    }

    function toggleTheme() {
        const currentTheme = document.documentElement.classList.contains('dark')
            ? 'dark'
            : 'light';
        setTheme(currentTheme === 'dark' ? 'light' : 'dark');
    }

    document.addEventListener('DOMContentLoaded', () => {
        const savedTheme = localStorage.getItem('theme') || 'light';
        setTheme(savedTheme);

        const mobileMenu = document.querySelector('.mobile-nav');
        const mobileMenuButton = document.getElementById('mobile-menu-button');

        mobileMenuButton.addEventListener('click', () => {
            mobileMenu.classList.toggle('hidden');
        });

        document.addEventListener('click', (e) => {
            if (!mobileMenuButton.contains(e.target) && !mobileMenu.contains(e.target)) {
                mobileMenu.classList.add('hidden');
            }
        });

        document.querySelectorAll('.theme-toggle').forEach(btn => {
            btn.addEventListener('click', toggleTheme);
        });
    });
})();
