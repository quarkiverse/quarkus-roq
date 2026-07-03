window.downloadQR = function(btn) {
  var wrap = btn.closest('.text-center').querySelector('.qr-wrap');
  var img = wrap.querySelector('img');
  if (!img) return;
  var filename = (wrap.dataset.filename || 'qr-code.png').toLowerCase();
  var canvas = document.createElement('canvas');
  canvas.width = 200;
  canvas.height = 200;
  var ctx = canvas.getContext('2d');
  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, 200, 200);
  ctx.drawImage(img, 0, 0, 200, 200);
  var a = document.createElement('a');
  a.download = filename;
  a.href = canvas.toDataURL('image/png');
  a.click();
};
