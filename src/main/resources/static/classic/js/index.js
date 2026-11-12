(function ($) {
  'use strict';

  App.api('GET', 'ping')
    .done(function () {
      App.showResult('#server-status', 'Serveur disponible', false);
    })
    .fail(function (jqXHR) {
      App.showResult('#server-status', 'Serveur indisponible : ' + App.errorMessage(jqXHR), true);
    });
})(jQuery);
