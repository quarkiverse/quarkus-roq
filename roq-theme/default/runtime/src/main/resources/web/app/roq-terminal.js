// Terminal tab switching
(function() {
    document.addEventListener('DOMContentLoaded', function() {
        document.querySelectorAll('.roq-terminal-tabs').forEach(function(tabBar) {
            var terminal = tabBar.closest('.roq-terminal');
            var tabs = terminal.querySelectorAll('.roq-terminal-tab');
            var panes = terminal.querySelectorAll('.roq-terminal-pane');

            tabs.forEach(function(tab) {
                tab.addEventListener('click', function() {
                    var index = tab.getAttribute('data-tab');
                    tabs.forEach(function(t) { t.classList.remove('active'); });
                    panes.forEach(function(p) { p.classList.remove('active'); });
                    tab.classList.add('active');
                    terminal.querySelector('.roq-terminal-pane[data-tab="' + index + '"]').classList.add('active');
                });
            });
        });
    });
})();