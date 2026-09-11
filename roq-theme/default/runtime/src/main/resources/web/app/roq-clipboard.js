// Copy-to-clipboard with toast
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

function copyAndToast(text) {
    navigator.clipboard.writeText(text).then(function() {
        showToast('Copied to clipboard');
    });
}

document.addEventListener('click', function(e) {
    var btn = e.target.closest('.roq-copy-btn');
    if (!btn) return;

    var target = btn.getAttribute('data-copy-target');
    var container = target
        ? document.querySelector(target)
        : btn.closest('.roq-terminal') || btn.closest('pre');
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
            copyAndToast(cmds.join('\n'));
            return;
        }
    }

    // Generic: copy from code element if inside pre, otherwise container text
    var codeEl = container.querySelector('code');
    copyAndToast((codeEl || container).textContent.trim());
});

export function enableCodeCopy() {
    document.querySelectorAll('pre > code').forEach(function(code) {
        var pre = code.parentElement;
        pre.classList.add('roq-code-copy');
        pre.style.position = 'relative';
        var btn = document.createElement('button');
        btn.className = 'roq-copy-btn code-copy-btn';
        btn.innerHTML = '<i class="fa-regular fa-copy"></i>';
        pre.appendChild(btn);
    });
}