/*
 * Gestes : appui long, liste triable à la souris, molette, canvas, SVG, focus clavier, copier-coller.
 */
(function ($) {
  'use strict';

  // --- Appui long : validé quand le bouton est maintenu 1,5 s ---
  var HOLD_MS = 1500;
  var holdStart = 0;
  var holdTimer = null;
  var holdDone = false;

  function stopHold(released) {
    if (!holdStart) {
      return;
    }
    clearInterval(holdTimer);
    var elapsed = Date.now() - holdStart;
    holdStart = 0;
    if (released && !holdDone) {
      $('#long-press-progress').val(0);
      App.showResult('#long-press-result', 'Appui trop court (' + elapsed + ' ms)', true);
    }
  }

  $('#btn-long-press').on('mousedown', function (event) {
    if (event.button !== 0) {
      return;
    }
    holdStart = Date.now();
    holdDone = false;
    App.showResult('#long-press-result', 'Appui en cours…', false);
    holdTimer = setInterval(function () {
      var elapsed = Date.now() - holdStart;
      $('#long-press-progress').val(Math.min(elapsed, HOLD_MS));
      if (elapsed >= HOLD_MS && !holdDone) {
        holdDone = true;
        App.showResult('#long-press-result', 'Appui long validé', false);
      }
    }, 50);
  });
  $(document).on('mouseup', function () {
    stopHold(true);
  });
  $('#btn-long-press').on('mouseleave', function () {
    stopHold(true);
  });

  // --- Liste triable à la souris (mousedown / mousemove / mouseup) ---
  var $dragged = null;

  function updateOrder() {
    var order = $('#sortable-list li').map(function () {
      return $(this).data('value');
    }).get();
    App.showResult('#sortable-result', 'Ordre : ' + order.join(', '), false);
  }

  $('#sortable-list').on('mousedown', 'li', function (event) {
    event.preventDefault();
    $dragged = $(this).addClass('dragging');
  });
  $(document).on('mousemove', function (event) {
    if (!$dragged) {
      return;
    }
    var under = document.elementFromPoint(event.clientX, event.clientY);
    var $over = $(under).closest('#sortable-list li');
    if ($over.length === 0 || $over[0] === $dragged[0]) {
      return;
    }
    var rect = $over[0].getBoundingClientRect();
    if (event.clientY < rect.top + rect.height / 2) {
      $over.before($dragged);
    } else {
      $over.after($dragged);
    }
  }).on('mouseup', function () {
    if ($dragged) {
      $dragged.removeClass('dragging');
      $dragged = null;
      updateOrder();
    }
  });

  // --- Molette et défilement horizontal ---
  var notches = 0;
  $('#wheel-zone').on('wheel', function (event) {
    notches += Math.sign(event.originalEvent.deltaY);
    App.showResult('#wheel-result', 'Molette : ' + notches + ' cran' + (Math.abs(notches) > 1 ? 's' : ''), false);
  });
  $('#wheel-target').on('click', function () {
    App.showResult('#wheel-result', 'Bouton du bas de la zone cliqué (molette : ' + notches + ')', false);
  });
  $('#hscroll-target').on('click', function () {
    App.showResult('#hscroll-result', 'Bouton tout à droite cliqué', false);
  });

  // --- Canvas de dessin ---
  var draw = document.getElementById('draw-canvas');
  var ctx = draw.getContext('2d');
  var drawing = false;
  var points = [];

  function canvasPoint(canvas, event) {
    var rect = canvas.getBoundingClientRect();
    return { x: event.clientX - rect.left, y: event.clientY - rect.top };
  }

  function drawSummary() {
    if (points.length === 0) {
      App.showResult('#draw-result', 'Tracé : 0 point', false);
      return;
    }
    var xs = points.map(function (p) { return p.x; });
    var width = Math.round(Math.max.apply(null, xs) - Math.min.apply(null, xs));
    App.showResult('#draw-result', 'Tracé : ' + points.length + ' points, largeur ' + width + ' px', false);
  }

  $(draw).on('mousedown', function (event) {
    drawing = true;
    var p = canvasPoint(draw, event);
    points.push(p);
    ctx.beginPath();
    ctx.moveTo(p.x, p.y);
  }).on('mousemove', function (event) {
    if (!drawing) {
      return;
    }
    var p = canvasPoint(draw, event);
    points.push(p);
    ctx.lineTo(p.x, p.y);
    ctx.strokeStyle = '#102a43';
    ctx.lineWidth = 2;
    ctx.stroke();
  });
  $(document).on('mouseup', function () {
    if (drawing) {
      drawing = false;
      drawSummary();
    }
  });
  $('#btn-draw-clear').on('click', function () {
    ctx.clearRect(0, 0, draw.width, draw.height);
    points = [];
    drawSummary();
  });

  // --- Cibles dessinées dans un canvas : clic par coordonnées ---
  var targets = [{ name: 'A', x: 70, y: 60 }, { name: 'B', x: 210, y: 60 }, { name: 'C', x: 350, y: 60 }];
  var tc = document.getElementById('targets-canvas');
  var tctx = tc.getContext('2d');
  targets.forEach(function (t) {
    tctx.beginPath();
    tctx.arc(t.x, t.y, 35, 0, 2 * Math.PI);
    tctx.fillStyle = '#d9e2ec';
    tctx.fill();
    tctx.fillStyle = '#102a43';
    tctx.font = 'bold 22px sans-serif';
    tctx.textAlign = 'center';
    tctx.fillText(t.name, t.x, t.y + 8);
  });
  $(tc).on('click', function (event) {
    var p = canvasPoint(tc, event);
    var hit = targets.filter(function (t) {
      return Math.hypot(p.x - t.x, p.y - t.y) <= 35;
    })[0];
    App.showResult('#canvas-click-result', hit ? 'Cible ' + hit.name + ' touchée' : 'Aucune cible touchée', !hit);
  });

  // --- SVG ---
  $('#svg-chart').on('click', '.svg-point', function () {
    App.showResult('#svg-result', 'Point ' + this.getAttribute('data-label') + ' : ' + this.getAttribute('data-value'), false);
  }).on('mouseenter', '.svg-point', function () {
    $('#svg-tooltip').text(this.getAttribute('data-label') + ' — ' + this.getAttribute('data-value'));
  }).on('mouseleave', '.svg-point', function () {
    $('#svg-tooltip').text('');
  });

  // --- Focus clavier ---
  var LABELS = { 'tab-field-1': 'champ 1', 'tab-field-2': 'champ 2', 'tab-field-3': 'champ 3', 'tab-custom': 'élément focalisable' };
  $('#section-focus').on('focus', '#tab-field-1, #tab-field-2, #tab-field-3, #tab-custom', function () {
    App.showResult('#focus-result', 'Focus : ' + LABELS[this.id], false);
  }).on('blur', '#tab-field-1, #tab-field-2, #tab-field-3, #tab-custom', function () {
    $('#blur-result').text('Sortie de : ' + LABELS[this.id]);
  });
  $('#tab-custom').on('keydown', function (event) {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      App.showResult('#tab-custom-result', 'Élément activé au clavier (' + (event.key === ' ' ? 'Espace' : 'Entrée') + ')', false);
    }
  });

  // --- Copier-coller ---
  $('#copy-source').on('copy', function () {
    $('#copy-result').text('Texte copié dans le presse-papiers');
  });
  $('#paste-target').on('paste', function (event) {
    var text = (event.originalEvent.clipboardData || window.clipboardData).getData('text');
    App.showResult('#paste-result', 'Collé : ' + text, false);
  });
})(jQuery);
