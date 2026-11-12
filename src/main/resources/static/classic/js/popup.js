(function ($) {
  'use strict';

  var origin = new URLSearchParams(window.location.search).get('from') || 'inconnue';
  $('#popup-origin').text(origin);
  document.title = 'Fenêtre secondaire — ' + origin;

  $('#popup-send').on('click', function () {
    if (window.opener && !window.opener.closed) {
      window.opener.postMessage({ type: 'popup-message', from: origin, text: $('#popup-input').val() }, window.location.origin);
      App.showResult('#popup-status', 'Message envoyé à la fenêtre d\'origine', false);
    } else {
      App.showResult('#popup-status', 'Aucune fenêtre d\'origine', true);
    }
  });

  $('#popup-close').on('click', function () {
    window.close();
  });
})(jQuery);
