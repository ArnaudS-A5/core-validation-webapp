(function ($) {
  'use strict';

  // Boutons génériques : un clic écrit le message prévu dans l'élément cible, à l'intérieur de la frame.
  $('.frame-button').on('click', function () {
    var $button = $(this);
    $('#' + $button.data('result')).text($button.data('message'));
  });

  // Formulaire dans la frame : le résultat est affiché localement (aucun appel serveur).
  $('#frame-form-form').on('submit', function (event) {
    event.preventDefault();
    var name = $('#frame-input').val().trim();
    var confirmation = $('#frame-checkbox').is(':checked') ? ' (confirmation demandée)' : '';
    $('#frame-form-result').text(name ? 'Bonjour ' + name + confirmation : 'Prénom manquant');
  });
})(jQuery);
