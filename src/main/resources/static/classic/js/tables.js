(function ($) {
  'use strict';

  var state = { q: '', sort: 'id', dir: 'asc', page: 0, size: 10, totalPages: 1 };
  var selected = {};   // id -> true
  var pendingDeleteIds = [];

  function selectedIds() {
    return $.map(selected, function (_, id) { return Number(id); });
  }

  function updateSelection() {
    var count = selectedIds().length;
    $('#selection-count').text(count + ' sélectionné(s)');
    $('#btn-delete-selected').prop('disabled', count === 0);
  }

  function renderRows(items) {
    var $body = $('#items-body').empty();
    $.each(items, function (_, item) {
      var $tr = $('<tr>', { id: 'row-' + item.id }).toggleClass('selected', !!selected[item.id]);
      $tr.append($('<td>').append($('<input>', { type: 'checkbox', id: 'row-check-' + item.id, 'class': 'row-check', 'data-id': item.id, checked: !!selected[item.id] })));
      $tr.append($('<td>', { text: item.id }));
      $tr.append($('<td>', { 'class': 'cell-name', id: 'name-' + item.id, text: item.name }));
      $tr.append($('<td>', { text: item.category }));
      $tr.append($('<td>', { text: item.price.toFixed(2) + ' €' }));
      $tr.append($('<td>')
        .append($('<button>', { type: 'button', id: 'edit-' + item.id, 'class': 'btn-edit', 'data-id': item.id, text: 'Modifier' }))
        .append(' ')
        .append($('<button>', { type: 'button', id: 'delete-' + item.id, 'class': 'btn-delete danger', 'data-id': item.id, text: 'Supprimer' })));
      $body.append($tr);
    });
  }

  function renderPagination() {
    var $nav = $('#pagination').empty();
    $nav.append($('<button>', { type: 'button', id: 'page-prev', text: 'Précédent', disabled: state.page === 0 }));
    for (var i = 0; i < state.totalPages; i++) {
      var $link = $('<button>', { type: 'button', id: 'page-link-' + (i + 1), 'class': 'page-link', 'data-page': i, text: i + 1 });
      if (i === state.page) { $link.attr('aria-current', 'page'); }
      $nav.append($link);
    }
    $nav.append($('<button>', { type: 'button', id: 'page-next', text: 'Suivant', disabled: state.page >= state.totalPages - 1 }));
  }

  function renderSortState() {
    $('th.sortable').attr('aria-sort', 'none');
    var attr = state.dir === 'asc' ? 'ascending' : 'descending';
    $('th.sortable[data-sort="' + state.sort + '"]').attr('aria-sort', attr);
  }

  function load() {
    $('#items-body').addClass('loading');
    $('#table-status').text('Chargement…');
    var query = 'items?page=' + state.page + '&size=' + state.size + '&sort=' + state.sort + '&dir=' + state.dir
      + '&q=' + encodeURIComponent(state.q);
    return App.api('GET', query)
      .done(function (data) {
        state.totalPages = data.totalPages;
        if (state.page >= data.totalPages) {
          state.page = data.totalPages - 1;
          load();
          return;
        }
        renderRows(data.items);
        renderPagination();
        renderSortState();
        $('#table-status').text(data.totalElements + ' résultat(s)');
        $('#check-all-rows').prop('checked', false);
        updateSelection();
      })
      .fail(function (jqXHR) {
        $('#table-status').text(App.errorMessage(jqXHR));
      })
      .always(function () {
        $('#items-body').removeClass('loading');
      });
  }

  // Recherche, taille de page, tri, pagination
  function applySearch() {
    state.q = $('#table-search').val();
    state.page = 0;
    load();
  }
  $('#btn-search').on('click', applySearch);
  $('#table-search').on('keydown', function (event) {
    if (event.key === 'Enter') { applySearch(); }
  });
  $('#page-size').on('change', function () {
    state.size = Number($(this).val());
    state.page = 0;
    load();
  });
  $('th.sortable').on('click', function () {
    var key = $(this).data('sort');
    state.dir = (state.sort === key && state.dir === 'asc') ? 'desc' : 'asc';
    state.sort = key;
    state.page = 0;
    load();
  });
  $('#pagination').on('click', 'button', function () {
    var id = this.id;
    if (id === 'page-prev') { state.page = Math.max(0, state.page - 1); }
    else if (id === 'page-next') { state.page = Math.min(state.totalPages - 1, state.page + 1); }
    else { state.page = Number($(this).data('page')); }
    load();
  });

  // Sélection
  $('#items-body').on('change', '.row-check', function () {
    var id = $(this).data('id');
    if (this.checked) { selected[id] = true; } else { delete selected[id]; }
    $(this).closest('tr').toggleClass('selected', this.checked);
    updateSelection();
  });
  $('#check-all-rows').on('change', function () {
    var checked = this.checked;
    $('#items-body .row-check').each(function () {
      $(this).prop('checked', checked).trigger('change');
    });
  });

  // Suppression avec confirmation (modale interne)
  function askDelete(ids, label) {
    pendingDeleteIds = ids;
    $('#confirm-text').text(label);
    $('#confirm-modal').css('display', 'flex');
  }
  $('#items-body').on('click', '.btn-delete', function () {
    var id = Number($(this).data('id'));
    askDelete([id], 'Supprimer l\'article n° ' + id + ' ?');
  });
  $('#btn-delete-selected').on('click', function () {
    var ids = selectedIds();
    askDelete(ids, 'Supprimer ' + ids.length + ' article(s) sélectionné(s) ?');
  });
  $('#confirm-no').on('click', function () {
    pendingDeleteIds = [];
    $('#confirm-modal').hide();
  });
  $('#confirm-yes').on('click', function () {
    var ids = pendingDeleteIds;
    pendingDeleteIds = [];
    $('#confirm-modal').hide();
    var requests = $.map(ids, function (id) {
      return App.api('DELETE', 'items/' + id).done(function () { delete selected[id]; });
    });
    $.when.apply($, requests).done(function () {
      App.showResult('#table-message', ids.length + ' article(s) supprimé(s)', false);
      load();
    }).fail(function (jqXHR) {
      App.showResult('#table-message', App.errorMessage(jqXHR), true);
      load();
    });
  });

  // Édition en ligne
  $('#items-body').on('click', '.btn-edit', function () {
    var id = $(this).data('id');
    var $cell = $('#name-' + id);
    var current = $cell.text();
    $cell.empty().append($('<input>', { type: 'text', id: 'edit-input-' + id, value: current }));
    $(this).replaceWith($('<button>', { type: 'button', id: 'save-' + id, 'class': 'btn-save primary', 'data-id': id, text: 'Enregistrer' }));
  });
  $('#items-body').on('click', '.btn-save', function () {
    var id = $(this).data('id');
    var name = $('#edit-input-' + id).val();
    App.api('PUT', 'items/' + id, { name: name })
      .done(function () {
        App.showResult('#table-message', 'Article ' + id + ' modifié', false);
        load();
      })
      .fail(function (jqXHR) {
        App.showResult('#table-message', App.errorMessage(jqXHR), true);
      });
  });

  // Ajout
  $('#add-form').on('submit', function (event) {
    event.preventDefault();
    $('#add-error').text('');
    var body = {
      name: $('#add-name').val(),
      category: $('#add-category').val(),
      price: $('#add-price').val() === '' ? null : Number($('#add-price').val())
    };
    App.api('POST', 'items', body)
      .done(function (data) {
        App.showResult('#table-message', 'Article « ' + data.item.name + ' » ajouté (n° ' + data.item.id + ')', false);
        $('#add-form')[0].reset();
        state.q = '';
        $('#table-search').val('');
        state.sort = 'id';
        state.dir = 'desc';
        state.page = 0;
        load();
      })
      .fail(function (jqXHR) {
        var errors = jqXHR.responseJSON && jqXHR.responseJSON.errors;
        $('#add-error').text(errors && errors.name ? errors.name : App.errorMessage(jqXHR));
      });
  });

  load();
})(jQuery);
