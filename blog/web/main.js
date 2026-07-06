import hljs from 'highlight.js';
import 'highlight.js/styles/github.css';
import './_hljs-dark.css';
import './_medium-zoom.css';
import mediumZoom from 'medium-zoom';
import { enableCodeCopy } from './roq-clipboard.js';

hljs.highlightAll();
enableCodeCopy();

mediumZoom('.content-prose img, .asciidoc-prose img', {
  background: 'rgba(0, 0, 0, 0.85)',
  margin: 24,
});

document.querySelectorAll('.content-prose a, .asciidoc-prose a').forEach(function(a) {
  if (a.hostname && a.hostname !== location.hostname) {
    a.target = '_blank';
    a.rel = 'noopener noreferrer';
  }
});