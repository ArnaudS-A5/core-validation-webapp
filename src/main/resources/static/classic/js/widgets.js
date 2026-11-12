(function ($) {
  'use strict';

  // --- Onglets ---
  $('[role=tab]').on('click', function () {
    var $tab = $(this);
    $('[role=tab]').attr('aria-selected', 'false');
    $tab.attr('aria-selected', 'true');
    $('.tab-panel').addClass('hidden');
    $('#' + $tab.attr('aria-controls')).removeClass('hidden');
  });

  // --- Accordéon animé ---
  $('.accordion-header').on('click', function () {
    var $header = $(this);
    var expanded = $header.attr('aria-expanded') === 'true';
    $header.attr('aria-expanded', String(!expanded));
    $header.next('.accordion-body').slideToggle(300);
  });

  // --- Modale ---
  function closeModal() {
    $('#modal').fadeOut(200);
  }
  $('#btn-open-modal').on('click', function () {
    $('#modal-input').val('');
    $('#modal').css('display', 'none').fadeIn(300).css('display', 'flex');
    $('#modal-input').trigger('focus');
  });
  $('#modal-close, #modal-cancel').on('click', function () {
    closeModal();
    App.showResult('#modal-result', 'Modale annulée', false);
  });
  $('#modal-ok').on('click', function () {
    var value = $('#modal-input').val();
    closeModal();
    App.showResult('#modal-result', 'Réponse saisie : ' + (value || '(vide)'), false);
  });

  // --- Menu au survol ---
  $('#submenu a').on('click', function (event) {
    event.preventDefault();
    App.showResult('#menu-result', 'Choix : ' + $(this).data('label'), false);
  });

  // --- Liste personnalisée ---
  $('#custom-select-button').on('click', function () {
    var open = $(this).attr('aria-expanded') === 'true';
    $(this).attr('aria-expanded', String(!open));
    $('#custom-select-list').toggle(!open);
  });
  $('#custom-select-list').on('click', 'li', function () {
    $('#custom-select-result').text('Couleur : ' + $(this).text());
    $('#custom-select-button').attr('aria-expanded', 'false').text($(this).text() + ' ▾');
    $('#custom-select-list').hide();
  });

  // --- Glisser-déposer HTML5 ---
  var dragItem = document.getElementById('drag-item');
  var dropTarget = document.getElementById('drop-target');
  dragItem.addEventListener('dragstart', function (event) {
    event.dataTransfer.setData('text/plain', 'drag-item');
    event.dataTransfer.effectAllowed = 'move';
  });
  dropTarget.addEventListener('dragover', function (event) {
    event.preventDefault();
    dropTarget.classList.add('over');
  });
  dropTarget.addEventListener('dragleave', function () {
    dropTarget.classList.remove('over');
  });
  dropTarget.addEventListener('drop', function (event) {
    event.preventDefault();
    dropTarget.classList.remove('over');
    dropTarget.appendChild(dragItem);
    App.showResult('#drag-result', 'Élément déposé dans la zone', false);
  });

  // --- Curseur manuel (événements souris) ---
  var track = document.getElementById('slider-track');
  var handle = document.getElementById('slider-handle');
  var dragging = false;
  function moveHandle(clientX) {
    var rect = track.getBoundingClientRect();
    var ratio = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
    var value = Math.round(ratio * 100);
    handle.style.left = (ratio * (rect.width - handle.offsetWidth)) + 'px';
    handle.setAttribute('aria-valuenow', String(value));
    document.getElementById('slider-value').textContent = String(value);
  }
  handle.addEventListener('mousedown', function (event) {
    dragging = true;
    event.preventDefault();
  });
  document.addEventListener('mousemove', function (event) {
    if (dragging) { moveHandle(event.clientX); }
  });
  document.addEventListener('mouseup', function () {
    dragging = false;
  });

  // --- Shadow DOM ---
  var host = document.getElementById('shadow-host');
  var root = host.attachShadow({ mode: 'open' });
  root.innerHTML = '<style>button{padding:.4rem .8rem;margin-right:.5rem}</style>'
    + '<button id="shadow-button" type="button">Bouton dans le Shadow DOM</button>'
    + '<span id="shadow-result">En attente</span>';
  root.getElementById('shadow-button').addEventListener('click', function () {
    root.getElementById('shadow-result').textContent = 'Bouton Shadow DOM cliqué';
  });

  // --- Clavier ---
  $('#key-input').on('keydown', function (event) {
    var label = event.key === ' ' ? 'Espace' : event.key;
    var modifiers = [];
    if (event.ctrlKey) { modifiers.push('Ctrl'); }
    if (event.shiftKey) { modifiers.push('Maj'); }
    if (event.altKey) { modifiers.push('Alt'); }
    App.showResult('#key-result', 'Touche : ' + modifiers.concat(label).join('+'), false);
  });

  // --- Zone défilante ---
  $('#scroll-target').on('click', function () {
    App.showResult('#scroll-result', 'Bouton en bas de zone cliqué', false);
  });
})(jQuery);
