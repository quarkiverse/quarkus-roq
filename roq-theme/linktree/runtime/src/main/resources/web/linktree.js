window.downloadQR = function(btn) {
  var wrap = btn.closest('.lt-tree-card').querySelector('.qr-wrap');
  var img = wrap.querySelector('img');
  if (!img) return;
  var filename = (wrap.dataset.filename || 'qr-code.svg').toLowerCase();
  var svgText = atob(img.src.split(',')[1]);
  var doc = new DOMParser().parseFromString(svgText, 'image/svg+xml');
  var svg = doc.querySelector('svg');
  svg.setAttribute('viewBox', '0 0 ' + svg.getAttribute('width') + ' ' + svg.getAttribute('height'));
  svg.setAttribute('width', '400');
  svg.setAttribute('height', '400');
  var blob = new Blob([new XMLSerializer().serializeToString(svg)], { type: 'image/svg+xml' });
  var a = document.createElement('a');
  a.download = filename;
  a.href = URL.createObjectURL(blob);
  a.click();
  URL.revokeObjectURL(a.href);
};
