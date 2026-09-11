(function() {
    'use strict';

    function generateSlug(text) {
        return text
            .toLowerCase()
            .replace(/[^\w\s-]/g, '')
            .replace(/\s+/g, '-')
            .replace(/--+/g, '-')
            .trim();
    }

    function generateAutoToc() {
        const tocList = document.getElementById('toc-list');
        if (!tocList) return false;

        if (tocList.children.length > 0) return false;

        const articleContent = document.querySelector('.article-content');
        if (!articleContent) return false;

        const headings = articleContent.querySelectorAll('h2, h3');
        if (headings.length === 0) return false;

        let sectionNum = 0;
        let currentH2 = null;
        let currentSublist = null;

        headings.forEach((heading) => {
            const text = heading.textContent.trim();
            let id = heading.id;

            if (!id) {
                id = generateSlug(text);
                heading.id = id;
            }

            if (heading.tagName === 'H2') {
                sectionNum++;

                const li = document.createElement('li');
                li.className = 'toc-item toc-h2';

                const link = document.createElement('a');
                link.href = '#' + id;
                link.className = 'toc-link';
                link.setAttribute('data-target', id);

                const numberSpan = document.createElement('span');
                numberSpan.className = 'toc-number';
                numberSpan.textContent = sectionNum + '.';
                link.appendChild(numberSpan);
                link.appendChild(document.createTextNode(' ' + text));

                li.appendChild(link);
                tocList.appendChild(li);

                currentH2 = li;
                currentSublist = null;
            } else if (heading.tagName === 'H3' && currentH2) {
                if (!currentSublist) {
                    currentSublist = document.createElement('ul');
                    currentSublist.className = 'toc-sublist';
                    currentH2.appendChild(currentSublist);
                }

                const li = document.createElement('li');
                li.className = 'toc-item toc-h3';

                const link = document.createElement('a');
                link.href = '#' + id;
                link.className = 'toc-link';
                link.setAttribute('data-target', id);

                const bulletSpan = document.createElement('span');
                bulletSpan.className = 'toc-bullet';
                bulletSpan.textContent = '•';
                link.appendChild(bulletSpan);
                link.appendChild(document.createTextNode(' ' + text));

                li.appendChild(link);
                currentSublist.appendChild(li);
            }
        });

        return true;
    }

    document.addEventListener('DOMContentLoaded', function() {
        generateAutoToc();
    });
})();

(function() {
    'use strict';

    document.addEventListener('DOMContentLoaded', function() {
        setTimeout(function() {
            const toc = document.querySelector('.toc-sticky');
            if (!toc) return;

            const tocLinks = toc.querySelectorAll('.toc-link');
            const allHeadings = document.querySelectorAll('h2[id], h3[id]');

            if (tocLinks.length === 0 || allHeadings.length === 0) return;

            const linkMap = new Map();
            tocLinks.forEach(link => {
                linkMap.set(link.dataset.target, link);
            });

            let isManualClick = false;
            let manualClickTimeout;
            let activeHeadingId = null;

            function setActiveLink(headingId) {
                if (!headingId) return;
                if (activeHeadingId === headingId) return;

                activeHeadingId = headingId;
                tocLinks.forEach(link => link.classList.remove('active'));

                const activeLink = linkMap.get(headingId);
                if (activeLink) {
                    activeLink.classList.add('active');
                }
            }

            function updateActiveToc() {
                if (isManualClick) return;

                const scrollPosition = window.scrollY + 200;
                let currentHeading = null;

                for (let i = 0; i < allHeadings.length; i++) {
                    const heading = allHeadings[i];
                    const headingTop = heading.offsetTop;

                    if (headingTop <= scrollPosition) {
                        currentHeading = heading;
                    } else {
                        break;
                    }
                }

                if (currentHeading && currentHeading.id) {
                    setActiveLink(currentHeading.id);
                }
            }

            let scrollTimeout;
            function handleScroll() {
                if (scrollTimeout) {
                    window.cancelAnimationFrame(scrollTimeout);
                }
                scrollTimeout = window.requestAnimationFrame(updateActiveToc);
            }

            window.addEventListener('scroll', handleScroll, { passive: true });

            tocLinks.forEach(link => {
                link.addEventListener('click', (e) => {
                    e.preventDefault();

                    isManualClick = true;
                    clearTimeout(manualClickTimeout);

                    const targetId = link.dataset.target;
                    setActiveLink(targetId);

                    const targetElement = document.getElementById(targetId);
                    if (targetElement) {
                        const headerOffset = 100;
                        const elementPosition = targetElement.offsetTop;
                        const offsetPosition = elementPosition - headerOffset;

                        window.scrollTo({
                            top: offsetPosition,
                            behavior: 'smooth'
                        });

                        history.pushState(null, null, '#' + targetId);
                    }

                    manualClickTimeout = setTimeout(() => {
                        isManualClick = false;
                        updateActiveToc();
                    }, 1000);
                });
            });

            updateActiveToc();

            if (window.location.hash) {
                const hash = window.location.hash.substring(1);
                setActiveLink(hash);

                setTimeout(() => {
                    const element = document.getElementById(hash);
                    if (element) {
                        const headerOffset = 100;
                        const elementPosition = element.offsetTop;
                        const offsetPosition = elementPosition - headerOffset;
                        window.scrollTo({
                            top: offsetPosition,
                            behavior: 'smooth'
                        });
                    }
                }, 100);
            }
        }, 100);
    });
})();
