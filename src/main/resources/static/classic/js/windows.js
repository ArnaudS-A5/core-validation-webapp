(function ($) {
  'use strict';

  var features = 'width=520,height=420,left=120,top=120';
  var popupCount = 0;

  function openPopup(name, origin) {
    var popup = window.open('popup.html?from=' + encodeURIComponent(origin), name, features);
    popupCount++;
    var text = popup ? 'Fenêtre « ' + name + ' » ouverte (' + popupCount + ' au total)' : 'Pop-up bloqué par le navigateur';
    App.showResult('#popup-open-result', text, !popup);
    return popup;
  }

  $('#btn-open-popup').on('click', function () {
    openPopup('popupWindow', 'popup');
  });

  $('#btn-open-two-popups').on('click', function () {
    openPopup('popupA', 'popup-A');
    openPopup('popupB', 'popup-B');
  });

  $('#btn-open-delayed').on('click', function () {
    App.showResult('#popup-open-result', 'Ouverture dans 2 secondes…', false);
    setTimeout(function () { openPopup('popupDelayed', 'popup-differe'); }, 2000);
  });

  // Message envoyé par la fenêtre ouverte via postMessage
  window.addEventListener('message', function (event) {
    if (event.origin !== window.location.origin || !event.data || event.data.type !== 'popup-message') {
      return;
    }
    App.showResult('#popup-message', 'Message de la fenêtre « ' + event.data.from + ' » : ' + event.data.text, false);
  });

  // Boîtes de dialogue natives
  $('#btn-alert').on('click', function () {
    window.alert('Ceci est une alerte');
    App.showResult('#alert-result', 'Alerte fermée', false);
  });

  $('#btn-confirm').on('click', function () {
    var ok = window.confirm('Confirmez-vous cette action ?');
    App.showResult('#confirm-result', ok ? 'Action confirmée' : 'Action annulée', false);
  });

  $('#btn-prompt').on('click', function () {
    var name = window.prompt('Quel est votre nom ?', 'Anonyme');
    App.showResult('#prompt-result', name === null ? 'Saisie annulée' : 'Bonjour ' + name, false);
  });

  $('#btn-alert-delayed').on('click', function () {
    App.showResult('#alert-delayed-result', 'Alerte dans 2 secondes…', false);
    setTimeout(function () {
      window.alert('Alerte différée');
      App.showResult('#alert-delayed-result', 'Alerte différée fermée', false);
    }, 2000);
  });

  $('#btn-redirect').on('click', function () {
    setTimeout(function () { window.location.href = 'popup.html?from=redirection'; }, 1000);
  });
})(jQuery);
