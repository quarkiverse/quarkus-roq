// Gallery Lightbox
(function () {
    var lightbox, lightboxImg, captionEl, counterEl, prevBtn, nextBtn, images, current;

    function el(tag, className, text) {
        var e = document.createElement(tag);
        if (className) e.className = className;
        if (text) e.textContent = text;
        return e;
    }

    function create() {
        lightbox = el('div', 'roq-lightbox');

        var closeBtn = el('button', 'roq-lightbox-close', '\u00d7');
        closeBtn.setAttribute('aria-label', 'Close');
        closeBtn.addEventListener('click', close);

        prevBtn = el('button', 'roq-lightbox-prev', '\u2039');
        prevBtn.setAttribute('aria-label', 'Previous');
        prevBtn.addEventListener('click', function () { show(current - 1); });

        nextBtn = el('button', 'roq-lightbox-next', '\u203a');
        nextBtn.setAttribute('aria-label', 'Next');
        nextBtn.addEventListener('click', function () { show(current + 1); });

        lightboxImg = document.createElement('img');
        lightboxImg.alt = '';

        captionEl = el('div', 'roq-lightbox-caption');
        counterEl = el('div', 'roq-lightbox-counter');

        lightbox.appendChild(closeBtn);
        lightbox.appendChild(prevBtn);
        lightbox.appendChild(lightboxImg);
        lightbox.appendChild(nextBtn);
        lightbox.appendChild(captionEl);
        lightbox.appendChild(counterEl);

        lightbox.addEventListener('click', function (e) {
            if (e.target === lightbox) close();
        });

        document.body.appendChild(lightbox);
    }

    function open(gallery, index) {
        images = Array.from(gallery.querySelectorAll('img'));
        if (!images.length) return;
        show(index);
        lightbox.classList.add('open');
        document.body.style.overflow = 'hidden';
    }

    function close() {
        lightbox.classList.remove('open');
        document.body.style.overflow = '';
    }

    function show(index) {
        current = (index + images.length) % images.length;
        var img = images[current];
        lightboxImg.src = img.src;
        lightboxImg.alt = img.alt || '';

        var fig = img.closest('figure');
        var cap = fig ? fig.querySelector('figcaption') : null;
        captionEl.textContent = cap ? cap.textContent : (img.alt || '');
        captionEl.style.display = captionEl.textContent ? '' : 'none';

        var multi = images.length > 1;
        counterEl.textContent = (current + 1) + ' / ' + images.length;
        counterEl.style.display = multi ? '' : 'none';
        prevBtn.style.display = multi ? '' : 'none';
        nextBtn.style.display = multi ? '' : 'none';
    }

    function onKey(e) {
        if (!lightbox || !lightbox.classList.contains('open')) return;
        if (e.key === 'Escape') close();
        else if (e.key === 'ArrowLeft') show(current - 1);
        else if (e.key === 'ArrowRight') show(current + 1);
    }

    function addSwipe() {
        var startX = 0;
        var threshold = 50;

        lightbox.addEventListener('touchstart', function (e) {
            startX = e.changedTouches[0].clientX;
        }, { passive: true });

        lightbox.addEventListener('touchend', function (e) {
            var dx = e.changedTouches[0].clientX - startX;
            if (Math.abs(dx) < threshold) return;
            if (dx > 0) show(current - 1);
            else show(current + 1);
        }, { passive: true });
    }

    document.addEventListener('DOMContentLoaded', function () {
        create();
        addSwipe();
        document.addEventListener('keydown', onKey);

        document.querySelectorAll('.roq-gallery').forEach(function (gallery) {
            gallery.querySelectorAll('img').forEach(function (img, i) {
                img.addEventListener('click', function () { open(gallery, i); });
            });
        });
    });
})();