import './site.css';
import './js/01-nav.js';
import './js/02-on-this-page.js';
import './js/03-fragment-jumper.js';
import './js/04-page-versions.js';
import './js/05-mobile-navbar.js';
import './js/06-copy-to-clipboard.js';
import './js/07-breadcrumbs.js';

import './img/home.svg';
import './img/home-o.svg';
import './img/back.svg';
import './img/caret.svg';
import './img/chevron.svg';
import './img/menu.svg';
import './img/octicons-16.svg';

import hljs from 'highlight.js';

window.hljs = hljs;

document.addEventListener('DOMContentLoaded', function() {
  document.querySelectorAll('pre.highlight > code, pre code.hljs[data-lang]').forEach(function(block) {
    hljs.highlightElement(block);
  });
});
