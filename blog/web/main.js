import hljs from 'highlight.js';
import 'highlight.js/styles/github.css';
import './_hljs-dark.css';
import './_medium-zoom.css';
import mediumZoom from 'medium-zoom';

hljs.highlightAll();

mediumZoom('.content-prose img, .asciidoc-prose img', {
  background: 'rgba(0, 0, 0, 0.85)',
  margin: 24,
});