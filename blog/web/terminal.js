(function() {
  document.addEventListener('DOMContentLoaded', function() {
    var slider = document.getElementById('rssFeedSlider');
    var pager = document.getElementById('rssPager');
    if (!slider || !pager) return;

    // Split lines into batches of 5
    var lines = Array.from(slider.querySelectorAll('.rss-line'));
    while (slider.firstChild) slider.removeChild(slider.firstChild);
    for (var i = 0; i < lines.length; i += 5) {
      var batch = document.createElement('div');
      batch.className = 'rss-batch';
      lines.slice(i, i + 5).forEach(function(line) { batch.appendChild(line); });
      slider.appendChild(batch);
    }

    var batches = slider.querySelectorAll('.rss-batch');
    var totalBatches = batches.length;
    var current = 0;
    var batchHeight = batches[0].offsetHeight;

    function nextPage() {
      pager.classList.add('pressing');
      setTimeout(function() { pager.classList.remove('pressing'); }, 200);
      setTimeout(function() {
        current = (current + 1) % totalBatches;
        slider.style.transition = 'transform 0.4s ease-in-out';
        slider.style.transform = 'translateY(-' + (current * batchHeight) + 'px)';
        if (current === 0) {
          slider.style.transition = 'none';
          slider.style.transform = 'translateY(0)';
        }
      }, 250);
    }

    document.addEventListener('keydown', function(e) {
      if (e.code === 'Space' || e.key === ' ') {
        var tag = e.target.tagName;
        if (tag === 'INPUT' || tag === 'TEXTAREA' || e.target.isContentEditable) return;
        e.preventDefault();
        nextPage();
      }
    });
  });
})();