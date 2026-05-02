// Copy-to-clipboard with toast
(function() {
    var toast = null;
    var hideTimeout = null;

    function showToast(msg) {
        if (!toast) {
            toast = document.createElement('div');
            toast.className = 'roq-toast';
            document.body.appendChild(toast);
        }
        toast.textContent = msg;
        clearTimeout(hideTimeout);
        toast.classList.remove('show');
        void toast.offsetWidth;
        toast.classList.add('show');
        hideTimeout = setTimeout(function() { toast.classList.remove('show'); }, 2000);
    }

    document.addEventListener('DOMContentLoaded', function() {
        document.querySelectorAll('.roq-copy-btn').forEach(function(btn) {
            btn.addEventListener('click', function() {
                var target = btn.getAttribute('data-copy-target');
                var container = target
                    ? document.querySelector(target)
                    : btn.closest('.roq-terminal');
                if (!container) return;

                // Terminal: extract commands from active pane or body
                var pane = container.querySelector('.roq-terminal-pane.active') ||
                           container.querySelector('.roq-terminal-body');
                if (pane) {
                    var cmds = [];
                    pane.querySelectorAll('.line').forEach(function(line) {
                        if (line.querySelector('.cmd')) {
                            var text = line.textContent.trim();
                            var prompt = line.querySelector('.prompt');
                            if (prompt) text = text.substring(prompt.textContent.length).trim();
                            cmds.push(text);
                        }
                    });
                    if (cmds.length > 0) {
                        navigator.clipboard.writeText(cmds.join('\n')).then(function() {
                            showToast('Copied to clipboard');
                        });
                        return;
                    }
                }

                // Generic: copy text content
                navigator.clipboard.writeText(container.textContent.trim()).then(function() {
                    showToast('Copied to clipboard');
                });
            });
        });
    });
})();