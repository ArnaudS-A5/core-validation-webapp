/*
 * Formulaires — saisies avancées : lecture seule, calendrier JavaScript, liste pilotée au clavier.
 */
(function ($) {
  'use strict';

  // --- Lecture seule : déverrouillage différé ---
  $('#btn-unlock-readonly').on('click', function () {
    var $button = $(this).prop('disabled', true);
    $('#unlock-spinner').show();
    setTimeout(function () {
      $('#input-readonly').prop('readonly', false).trigger('focus');
      $('#unlock-spinner').hide();
      $button.prop('disabled', false);
      App.showResult('#readonly-result', 'Référence déverrouillée', false);
    }, 1500);
  });
  $('#input-readonly').on('change', function () {
    App.showResult('#readonly-result', 'Référence saisie : ' + $(this).val(), false);
  });

  // --- Calendrier JavaScript ---
  var MONTHS = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août',
    'septembre', 'octobre', 'novembre', 'décembre'];
  var shown = new Date();
  shown.setDate(1);

  function pad(n) {
    return n < 10 ? '0' + n : String(n);
  }

  function renderCalendar() {
    var year = shown.getFullYear();
    var month = shown.getMonth();
    $('#datepicker-month').text(MONTHS[month] + ' ' + year);
    var $days = $('#datepicker-days').empty();
    ['lu', 'ma', 'me', 'je', 've', 'sa', 'di'].forEach(function (d) {
      $days.append($('<span>', { 'class': 'datepicker-weekday', text: d }));
    });
    var offset = (new Date(year, month, 1).getDay() + 6) % 7; // lundi = 0
    for (var i = 0; i < offset; i++) {
      $days.append($('<span>'));
    }
    var count = new Date(year, month + 1, 0).getDate();
    for (var day = 1; day <= count; day++) {
      var weekday = new Date(year, month, day).getDay();
      $days.append($('<button>', {
        type: 'button',
        id: 'datepicker-day-' + day,
        'class': 'datepicker-day',
        'data-date': pad(day) + '/' + pad(month + 1) + '/' + year,
        disabled: weekday === 0 || weekday === 6,
        text: day
      }));
    }
  }

  $('#datepicker-input').on('click', function () {
    renderCalendar();
    $('#datepicker').removeClass('hidden');
  });
  $('#datepicker-prev').on('click', function () {
    shown.setMonth(shown.getMonth() - 1);
    renderCalendar();
  });
  $('#datepicker-next').on('click', function () {
    shown.setMonth(shown.getMonth() + 1);
    renderCalendar();
  });
  $('#datepicker-days').on('click', '.datepicker-day', function () {
    var value = $(this).data('date');
    $('#datepicker-input').val(value);
    $('#datepicker').addClass('hidden');
    App.showResult('#datepicker-result', 'Date choisie : ' + value, false);
  });

  // --- Liste à saisie pilotée au clavier (combobox) ---
  var countries = null;
  var active = -1;

  function options() {
    return $('#combo-list [role=option]');
  }

  function setActive(index) {
    var $options = options();
    if ($options.length === 0) {
      return;
    }
    active = (index + $options.length) % $options.length;
    $options.removeClass('active').attr('aria-selected', 'false');
    var $current = $options.eq(active).addClass('active').attr('aria-selected', 'true');
    $('#combo-input').attr('aria-activedescendant', $current.attr('id'));
  }

  function closeList() {
    $('#combo-list').addClass('hidden');
    $('#combo-input').attr('aria-expanded', 'false').removeAttr('aria-activedescendant');
    active = -1;
  }

  function choose($option) {
    $('#combo-input').val($option.text());
    closeList();
    App.showResult('#combo-result', 'Pays choisi : ' + $option.text() + ' (' + $option.data('code') + ')', false);
  }

  function renderList(filter) {
    var needle = filter.trim().toLowerCase();
    var $list = $('#combo-list').empty();
    countries.filter(function (c) {
      return c.name.toLowerCase().indexOf(needle) !== -1;
    }).forEach(function (c, i) {
      $list.append($('<li>', { id: 'combo-option-' + i, role: 'option', 'data-code': c.code, text: c.name }));
    });
    var hasOptions = options().length > 0;
    $list.toggleClass('hidden', !hasOptions);
    $('#combo-input').attr('aria-expanded', String(hasOptions));
    active = -1;
  }

  $('#combo-input').on('input', function () {
    var value = $(this).val();
    if (countries) {
      renderList(value);
      return;
    }
    App.api('GET', 'countries').done(function (data) {
      countries = data;
      renderList($('#combo-input').val());
    });
  }).on('keydown', function (event) {
    if ($('#combo-list').hasClass('hidden')) {
      return;
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setActive(active + 1);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActive(active - 1);
    } else if (event.key === 'Enter' && active >= 0) {
      event.preventDefault();
      choose(options().eq(active));
    } else if (event.key === 'Escape') {
      closeList();
    }
  });
  $('#combo-list').on('mousedown', '[role=option]', function (event) {
    event.preventDefault();
    choose($(this));
  });
})(jQuery);
