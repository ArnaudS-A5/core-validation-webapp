(function ($) {
  'use strict';

  function showConnected(displayName, token) {
    $('#welcome-message').text('Bienvenue, ' + displayName + ' !');
    $('#session-token').text(token);
    $('#login-section').addClass('hidden');
    $('#welcome-panel').removeClass('hidden');
  }

  $('#login-form').on('submit', function (event) {
    event.preventDefault();
    var username = $('#login-username').val();
    var password = $('#login-password').val();

    if (!username || !password) {
      App.showResult('#login-message', 'Identifiant et mot de passe obligatoires', true);
      return;
    }

    App.showResult('#login-message', '', false);
    $('#login-submit').prop('disabled', true);
    $('#login-spinner').show();

    App.api('POST', 'login', { username: username, password: password })
      .done(function (data) {
        sessionStorage.setItem('socle.token', data.token);
        showConnected(data.displayName, data.token);
      })
      .fail(function (jqXHR) {
        App.showResult('#login-message', App.errorMessage(jqXHR), true);
      })
      .always(function () {
        $('#login-spinner').hide();
        $('#login-submit').prop('disabled', false);
      });
  });

  $('#logout-button').on('click', function () {
    sessionStorage.removeItem('socle.token');
    $('#welcome-panel').addClass('hidden');
    $('#login-section').removeClass('hidden');
    $('#login-form')[0].reset();
    App.showResult('#login-message', 'Vous êtes déconnecté', false);
  });
})(jQuery);
