/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
/*
 * Bookmark management interface, displayed by the {{bookmarks/}} macro: folder tree, drag and drop, keyboard
 * reordering, inline renaming, panel selection and automatic saving with conflict detection.
 *
 * Security: every text coming from the server or the user is inserted with textContent or as an attribute value,
 * never as HTML. The server checks all the rights again.
 */
define('advanced-bookmarks-manager-messages', {
  prefix: 'advancedbookmarks.',
  keys: [
    'manager.newFolder',
    'manager.newFolderName',
    'manager.addSubfolder',
    'manager.uncategorized',
    'manager.root',
    'manager.filter',
    'manager.panel',
    'manager.panelCount',
    'manager.panelCountAuto',
    'manager.panelLimitReached',
    'manager.rename',
    'manager.moveUp',
    'manager.moveDown',
    'manager.moveTo',
    'manager.remove',
    'manager.deleteFolder',
    'manager.confirmRemove',
    'manager.confirmDeleteFolder',
    'manager.deleted',
    'manager.inaccessible',
    'manager.empty',
    'manager.emptyFolder',
    'manager.noResult',
    'manager.loading',
    'manager.saving',
    'manager.saved',
    'manager.saveFailed',
    'manager.retry',
    'manager.conflict',
    'manager.loadFailed',
    'manager.expand',
    'manager.collapse',
    'manager.labelPlaceholder',
    'manager.userPicker',
    'error.forbidden',
    'error.invalidToken',
    'error.methodNotAllowed',
    'error.invalid',
    'error.conflict',
    'error.storageError',
    'error.unavailable',
    'error.unknownOperation',
    'error.network'
  ]
});
require(['jquery', 'xwiki-meta', 'xwiki-l10n!advanced-bookmarks-manager-messages'], function($, xm, l10n) {
  'use strict';

  // Mirrors of the server-side limits (BookmarkNormalizer), so that the user sees what will be saved.
  var MAX_FOLDER_NAME_LENGTH = 100;
  var MAX_LABEL_LENGTH = 200;
  var SAVE_DELAY = 400;
  var CONFLICT = 409;
  var ROOT = '';
  // Key of the uncategorized folder in the collapsed folders, since its identifier is empty.
  var UNCATEGORIZED_KEY = '__uncategorized';
  // Value of the first option of the "Move to" lists, which does not move anything.
  var NO_MOVE = '__';
  // Protection against cycles in corrupted data.
  var MAX_DEPTH = 100;

  var serviceReference = XWiki.Model.resolve('AdvancedBookmarks.Code.Service', XWiki.EntityType.DOCUMENT);
  var serviceURL = new XWiki.Document(serviceReference).getURL('get');

  var t = function(key) {
    return l10n.get.apply(l10n, ['manager.' + key].concat(Array.prototype.slice.call(arguments, 1)));
  };

  var notify = function(message, type) {
    new XWiki.widgets.Notification(message, type || 'done');
  };

  /**
   * Create an element. Attributes: "text" sets the text content, "className" the class, a false, null or undefined
   * value is ignored, a property of the element (such as "checked") is set as such, anything else as an attribute.
   */
  var createElement = function(tagName, attributes, children) {
    var element = document.createElement(tagName);
    Object.keys(attributes || {}).forEach(function(name) {
      var value = attributes[name];
      if (value === null || value === undefined || value === false) {
        return;
      }
      if (name === 'text') {
        element.textContent = value;
      } else if (name === 'className') {
        element.className = value;
      } else if (name in element && name.indexOf('-') < 0 && name !== 'draggable') {
        element[name] = value;
      } else {
        element.setAttribute(name, value === true ? '' : value);
      }
    });
    (children || []).forEach(function(child) {
      if (child !== null && child !== undefined) {
        element.appendChild(typeof child === 'string' ? document.createTextNode(child) : child);
      }
    });
    return element;
  };

  var createButton = function(action, symbol, title, className) {
    return createElement('button', {
      type: 'button',
      className: 'advanced-bookmarks-button ' + (className || ''),
      'data-action': action,
      title: title,
      'aria-label': title,
      text: symbol
    });
  };

  var normalizeForSearch = function(text) {
    return String(text || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  };

  /**
   * Administrators open the bookmarks of another user in a new tab, using the user picker of XWiki.
   */
  var createUserPicker = function(container) {
    var id = 'advanced-bookmarks-user-' + Math.random().toString(36).slice(2, 9);
    var select = createElement('select', {id: id, 'data-userScope': container.getAttribute('data-user-scope')});
    var picker = createElement('div', {className: 'advanced-bookmarks-user-picker'}, [
      createElement('label', {'for': id, text: t('userPicker')}),
      select
    ]);
    require(['xwiki-suggestUsers'], function() {
      $(select).suggestUsers({maxItems: 1}).on('change', function() {
        var user = $(this).val();
        if (user) {
          window.open(XWiki.currentDocument.getURL('view', 'user=' + encodeURIComponent(user)), '_blank',
            'noopener');
          this.selectize.clear(true);
        }
      });
    });
    return picker;
  };

  var initManager = function(container) {
    var user = container.getAttribute('data-user') || '';
    var panelLimit = parseInt(container.getAttribute('data-panel-limit'), 10) || 10;
    // The version is kept as the string sent by the server: Solr versions exceed the precision of JavaScript numbers.
    var state = {folders: [], bookmarks: [], version: '0'};

    // ------------------------------------------------------------------
    // Collapsed folders, remembered in the browser for each managed user.
    // ------------------------------------------------------------------
    var collapsedStorageKey = 'advancedbookmarks.collapsed.' + user;
    var collapsed = {};
    try {
      collapsed = JSON.parse(window.localStorage.getItem(collapsedStorageKey) || '{}') || {};
    } catch (e) {
      collapsed = {};
    }
    var folderKey = function(id) {
      return id === ROOT ? UNCATEGORIZED_KEY : id;
    };
    var setCollapsed = function(id, value) {
      if (value) {
        collapsed[folderKey(id)] = true;
      } else {
        delete collapsed[folderKey(id)];
      }
      try {
        window.localStorage.setItem(collapsedStorageKey, JSON.stringify(collapsed));
      } catch (e) {
        // The local storage is not available: folders are simply expanded again on the next visit.
      }
    };

    // ------------------------------------------------------------------
    // Data model
    // ------------------------------------------------------------------
    var byOrder = function(a, b) {
      return (a.order || 0) - (b.order || 0);
    };
    var getSubfolders = function(parent) {
      return state.folders.filter(function(folder) { return folder.parent === parent; }).sort(byOrder);
    };
    var getBookmarksOf = function(folder) {
      return state.bookmarks.filter(function(bookmark) { return bookmark.folder === folder; }).sort(byOrder);
    };
    var getFolder = function(id) {
      return state.folders.find(function(folder) { return folder.id === id; }) || null;
    };
    var getBookmark = function(page) {
      return state.bookmarks.find(function(bookmark) { return bookmark.page === page; }) || null;
    };
    var renumber = function(items) {
      items.forEach(function(item, index) { item.order = index; });
    };
    // Whether the candidate folder is the ancestor folder or one of its descendants.
    var isInBranch = function(candidate, ancestor) {
      var current = candidate;
      for (var depth = 0; current && depth < MAX_DEPTH; depth++) {
        if (current === ancestor) {
          return true;
        }
        var folder = getFolder(current);
        current = folder ? folder.parent : ROOT;
      }
      return false;
    };
    var getDescendants = function(id) {
      var result = [];
      getSubfolders(id).forEach(function(folder) {
        result.push(folder);
        result = result.concat(getDescendants(folder.id));
      });
      return result;
    };
    var getDepth = function(id) {
      var depth = 0;
      var folder = getFolder(id);
      while (folder && folder.parent && depth < MAX_DEPTH) {
        depth++;
        folder = getFolder(folder.parent);
      }
      return depth;
    };
    var getPath = function(id) {
      if (!id) {
        return t('uncategorized');
      }
      var names = [];
      var folder = getFolder(id);
      for (var depth = 0; folder && depth < MAX_DEPTH; depth++) {
        names.unshift(folder.name);
        folder = getFolder(folder.parent);
      }
      return names.join(' / ');
    };
    var getText = function(bookmark) {
      return bookmark.label || bookmark.page;
    };
    var countPanelBookmarks = function() {
      return state.bookmarks.filter(function(bookmark) { return bookmark.panel; }).length;
    };
    // Folder identifiers must match [A-Za-z0-9_-]{1,40} on the server.
    var newFolderId = function() {
      return 'f' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
    };

    var moveBookmark = function(bookmark, folder, index) {
      renumber(getBookmarksOf(bookmark.folder).filter(function(item) { return item !== bookmark; }));
      var target = getBookmarksOf(folder).filter(function(item) { return item !== bookmark; });
      if (typeof index !== 'number' || index < 0 || index > target.length) {
        index = target.length;
      }
      target.splice(index, 0, bookmark);
      bookmark.folder = folder;
      renumber(target);
    };
    var moveFolder = function(folder, parent, index) {
      if (parent && isInBranch(parent, folder.id)) {
        return false;
      }
      renumber(getSubfolders(folder.parent).filter(function(item) { return item !== folder; }));
      var target = getSubfolders(parent).filter(function(item) { return item !== folder; });
      if (typeof index !== 'number' || index < 0 || index > target.length) {
        index = target.length;
      }
      target.splice(index, 0, folder);
      folder.parent = parent;
      renumber(target);
      return true;
    };
    var shift = function(items, item, direction) {
      var from = items.indexOf(item);
      var to = from + direction;
      if (from < 0 || to < 0 || to >= items.length) {
        return false;
      }
      items.splice(from, 1);
      items.splice(to, 0, item);
      renumber(items);
      return true;
    };
    // The display order, which is also the order of the panel: uncategorized bookmarks first, then each folder with
    // its subfolders before its own bookmarks.
    var getDisplayOrder = function() {
      var result = getBookmarksOf(ROOT).slice();
      var visit = function(parent) {
        getSubfolders(parent).forEach(function(folder) {
          visit(folder.id);
          result = result.concat(getBookmarksOf(folder.id));
        });
      };
      visit(ROOT);
      return result;
    };

    // ------------------------------------------------------------------
    // Service calls
    // ------------------------------------------------------------------
    var call = function(parameters, post) {
      parameters.user = user;
      var body = new URLSearchParams(parameters);
      var options = {credentials: 'same-origin', headers: {'Accept': 'application/json'}};
      var url = serviceURL;
      if (post) {
        body.append('form_token', xm.form_token);
        options.method = 'POST';
        options.body = body;
      } else {
        url += (url.indexOf('?') < 0 ? '?' : '&') + body.toString();
      }
      return fetch(url, options).then(function(response) {
        return response.json().catch(function() {
          return {};
        }).then(function(json) {
          if (!response.ok) {
            var error = new Error(l10n.get('error.' + (json.error || 'network')));
            error.status = response.status;
            throw error;
          }
          return json;
        });
      }, function() {
        throw new Error(l10n.get('error.network'));
      });
    };

    var load = function() {
      return call({operation: 'getBookmarks'}).then(function(data) {
        state.folders = (data.folders || []).map(function(folder) {
          return {id: folder.id, name: folder.name, parent: folder.parent || ROOT, order: folder.order || 0};
        });
        state.bookmarks = (data.bookmarks || []).map(function(bookmark) {
          return {
            page: bookmark.page,
            label: bookmark.label || '',
            folder: bookmark.folder || ROOT,
            order: bookmark.order || 0,
            panel: !!bookmark.panel,
            viewable: !!bookmark.viewable,
            exists: bookmark.exists !== false,
            url: bookmark.url
          };
        });
        state.version = String(data.version || '0');
        render();
      });
    };

    // Automatic saving: changes are grouped, and a single save runs at a time.
    var saveTimer = null;
    var saving = false;
    var saveAgain = false;

    var changed = function() {
      render();
      showStatus(t('saving'), 'progress');
      window.clearTimeout(saveTimer);
      saveTimer = window.setTimeout(save, SAVE_DELAY);
    };

    var save = function() {
      if (saving) {
        saveAgain = true;
        return;
      }
      saving = true;
      var data = {
        folders: state.folders.map(function(folder) {
          return {id: folder.id, name: folder.name, parent: folder.parent, order: folder.order};
        }),
        bookmarks: getDisplayOrder().map(function(bookmark) {
          return {
            page: bookmark.page,
            label: bookmark.label,
            folder: bookmark.folder,
            order: bookmark.order,
            panel: bookmark.panel
          };
        })
      };
      call({operation: 'save', data: JSON.stringify(data), version: state.version}, true).then(function(result) {
        state.version = result.version;
        showStatus(t('saved'), 'success');
      }).catch(function(error) {
        if (error.status === CONFLICT) {
          // Modified meanwhile (another tab, the star): the stored bookmarks win and are displayed again.
          saveAgain = false;
          notify(t('conflict'), 'warning');
          return load().then(function() {
            showStatus('', '');
          });
        }
        showStatus(t('saveFailed') + ' ' + error.message, 'error', true);
      }).catch(function(error) {
        showStatus(t('loadFailed') + ' ' + error.message, 'error');
      }).then(function() {
        saving = false;
        if (saveAgain) {
          saveAgain = false;
          save();
        }
      });
    };

    // ------------------------------------------------------------------
    // Rendering. The toolbar is built once, so that the filter keeps the focus.
    // ------------------------------------------------------------------
    var filterInput = createElement('input', {
      type: 'search',
      className: 'advanced-bookmarks-filter',
      placeholder: t('filter'),
      'aria-label': t('filter')
    });
    var panelCounter = createElement('span', {className: 'advanced-bookmarks-counter'});
    var status = createElement('span', {className: 'advanced-bookmarks-status', role: 'status',
      'aria-live': 'polite'});
    // The filter has its own line: next to the "New folder" button, it looked like the field to name the folder.
    var toolbar = createElement('div', {className: 'advanced-bookmarks-toolbar'}, [
      createElement('div', {className: 'advanced-bookmarks-toolbar-row'}, [
        createElement('button', {type: 'button', className: 'btn btn-primary btn-sm', 'data-action': 'newFolder',
          text: '+ ' + t('newFolder')}),
        panelCounter,
        status
      ]),
      createElement('div', {className: 'advanced-bookmarks-toolbar-row'}, [filterInput])
    ]);
    var tree = createElement('div', {className: 'advanced-bookmarks-content'});

    var showStatus = function(text, type, withRetry) {
      status.textContent = text;
      status.className = 'advanced-bookmarks-status' + (type ? ' advanced-bookmarks-status-' + type : '');
      if (withRetry) {
        status.appendChild(document.createTextNode(' '));
        status.appendChild(createElement('button', {type: 'button', className: 'btn btn-default btn-xs',
          'data-action': 'retry', text: t('retry')}));
      }
    };

    var createMoveList = function(forFolder, currentValue, movedFolder) {
      var options = [
        createElement('option', {value: NO_MOVE, text: t('moveTo')}),
        createElement('option', {value: ROOT, text: forFolder ? t('root') : t('uncategorized'),
          disabled: currentValue === ROOT})
      ];
      var addFolders = function(parent) {
        getSubfolders(parent).forEach(function(folder) {
          // A folder cannot be moved into itself or into one of its descendants.
          if (forFolder && isInBranch(folder.id, movedFolder)) {
            return;
          }
          var indent = new Array(getDepth(folder.id) + 1).join(' ');
          options.push(createElement('option', {value: folder.id, text: indent + folder.name,
            disabled: folder.id === currentValue}));
          addFolders(folder.id);
        });
      };
      addFolders(ROOT);
      return createElement('select', {
        className: 'advanced-bookmarks-move',
        'data-action': forFolder ? 'moveFolder' : 'moveBookmark',
        title: t('moveTo'),
        'aria-label': t('moveTo')
      }, options);
    };

    // The bookmarks displayed in the panel when none is selected for it.
    var automaticPanel = [];

    var createBookmarkItem = function(bookmark, options) {
      options = options || {};
      var sortable = options.sortable !== false;
      var text = getText(bookmark);
      var link;
      if (bookmark.viewable && bookmark.exists) {
        link = createElement('a', {href: bookmark.url, className: 'advanced-bookmarks-link', title: bookmark.page,
          text: text});
      } else {
        link = createElement('span', {className: 'advanced-bookmarks-link advanced-bookmarks-link-unavailable',
          title: bookmark.page}, [
          text, ' ',
          createElement('span', {className: 'advanced-bookmarks-badge',
            text: bookmark.viewable ? t('deleted') : t('inaccessible')})
        ]);
      }
      var limitReached = !bookmark.panel && countPanelBookmarks() >= panelLimit;
      var checkboxId = 'advanced-bookmarks-panel-' + Math.random().toString(36).slice(2, 9);
      var panelLabel = createElement('label', {
        className: 'advanced-bookmarks-panel' + (bookmark.panel ? ' advanced-bookmarks-panel-selected' : ''),
        'for': checkboxId,
        title: limitReached ? t('panelLimitReached', panelLimit) : t('panel')
      }, [
        createElement('input', {type: 'checkbox', id: checkboxId, 'data-action': 'panel', checked: bookmark.panel,
          disabled: limitReached}),
        ' ' + t('panel')
      ]);

      var actions = [createButton('rename', '✎', t('rename'))];
      if (sortable) {
        actions.push(createButton('moveUp', '▲', t('moveUp')));
        actions.push(createButton('moveDown', '▼', t('moveDown')));
      }
      actions.push(createMoveList(false, bookmark.folder));
      actions.push(createButton('remove', '✕', t('remove'), 'advanced-bookmarks-button-danger'));

      return createElement('li', {
        className: 'advanced-bookmarks-bookmark' + (bookmark.panel ? ' advanced-bookmarks-bookmark-panel' : '') +
          (automaticPanel.indexOf(bookmark) >= 0 ? ' advanced-bookmarks-bookmark-auto' : ''),
        'data-page': bookmark.page,
        draggable: sortable ? 'true' : null
      }, [
        sortable ? createElement('span', {className: 'advanced-bookmarks-handle', 'aria-hidden': 'true',
          text: '⠇'}) : null,
        panelLabel,
        createElement('span', {className: 'advanced-bookmarks-text'}, [
          link,
          options.showPath ? createElement('span', {className: 'advanced-bookmarks-path',
            text: getPath(bookmark.folder)}) : null
        ]),
        createElement('span', {className: 'advanced-bookmarks-actions'}, actions)
      ]);
    };

    var countBookmarks = function(id) {
      var count = getBookmarksOf(id).length;
      if (id) {
        getDescendants(id).forEach(function(folder) { count += getBookmarksOf(folder.id).length; });
      }
      return count;
    };

    // The uncategorized folder is passed as null: it has no name, no actions and no subfolders.
    var createFolderItem = function(folder) {
      var id = folder ? folder.id : ROOT;
      var isCollapsed = !!collapsed[folderKey(id)];
      var children = [];
      if (folder) {
        getSubfolders(id).forEach(function(subfolder) { children.push(createFolderItem(subfolder)); });
      }
      getBookmarksOf(id).forEach(function(bookmark) { children.push(createBookmarkItem(bookmark)); });
      if (!children.length) {
        children.push(createElement('li', {className: 'advanced-bookmarks-empty', 'data-folder': id,
          text: t('emptyFolder')}));
      }

      var actions = folder ? [
        createButton('addSubfolder', '+', t('addSubfolder')),
        createButton('renameFolder', '✎', t('rename')),
        createButton('moveFolderUp', '▲', t('moveUp')),
        createButton('moveFolderDown', '▼', t('moveDown')),
        createMoveList(true, folder.parent, folder.id),
        createButton('deleteFolder', '✕', t('deleteFolder'), 'advanced-bookmarks-button-danger')
      ] : [];

      var header = createElement('div', {
        className: 'advanced-bookmarks-folder-header',
        'data-folder': id,
        draggable: folder ? 'true' : null
      }, [
        createElement('button', {
          type: 'button',
          className: 'advanced-bookmarks-toggle',
          'data-action': 'toggle',
          'aria-expanded': isCollapsed ? 'false' : 'true',
          title: isCollapsed ? t('expand') : t('collapse'),
          'aria-label': isCollapsed ? t('expand') : t('collapse'),
          text: isCollapsed ? '▸' : '▾'
        }),
        createElement('span', {className: 'advanced-bookmarks-folder-name',
          text: folder ? folder.name : t('uncategorized')}),
        createElement('span', {className: 'advanced-bookmarks-count', text: String(countBookmarks(id))}),
        createElement('span', {className: 'advanced-bookmarks-actions'}, actions)
      ]);

      return createElement('li', {
        className: 'advanced-bookmarks-folder' + (folder ? '' : ' advanced-bookmarks-uncategorized') +
          (isCollapsed ? ' advanced-bookmarks-collapsed' : ''),
        'data-folder': id
      }, [header, createElement('ul', {className: 'advanced-bookmarks-children'}, children)]);
    };

    var render = function() {
      var panelCount = countPanelBookmarks();
      automaticPanel = panelCount === 0 ? getDisplayOrder().slice(0, panelLimit) : [];
      panelCounter.textContent = panelCount === 0 && state.bookmarks.length ?
        t('panelCountAuto', Math.min(panelLimit, state.bookmarks.length)) : t('panelCount', panelCount, panelLimit);
      panelCounter.classList.toggle('advanced-bookmarks-counter-full', panelCount >= panelLimit);
      tree.textContent = '';

      var search = normalizeForSearch(filterInput.value.trim());
      if (search) {
        var results = getDisplayOrder().filter(function(bookmark) {
          return normalizeForSearch(getText(bookmark) + ' ' + bookmark.page + ' ' + getPath(bookmark.folder))
            .indexOf(search) >= 0;
        });
        if (!results.length) {
          tree.appendChild(createElement('p', {className: 'advanced-bookmarks-message', text: t('noResult')}));
          return;
        }
        tree.appendChild(createElement('ul', {className: 'advanced-bookmarks-tree advanced-bookmarks-results'},
          results.map(function(bookmark) {
            return createBookmarkItem(bookmark, {showPath: true, sortable: false});
          })));
        return;
      }

      if (!state.bookmarks.length && !state.folders.length) {
        tree.appendChild(createElement('p', {className: 'advanced-bookmarks-message', text: t('empty')}));
      }
      var list = createElement('ul', {className: 'advanced-bookmarks-tree'}, [createFolderItem(null)]);
      getSubfolders(ROOT).forEach(function(folder) { list.appendChild(createFolderItem(folder)); });
      tree.appendChild(list);
    };

    // ------------------------------------------------------------------
    // Inline editing
    // ------------------------------------------------------------------
    var editInline = function(target, value, placeholder, validate) {
      var input = createElement('input', {type: 'text', className: 'advanced-bookmarks-edit',
        placeholder: placeholder || ''});
      input.value = value;
      var done = false;
      // Dragging would prevent selecting text in the field.
      var draggable = target.closest('[draggable]');
      if (draggable) {
        draggable.setAttribute('draggable', 'false');
      }
      var finish = function(accept) {
        if (done) {
          return;
        }
        done = true;
        if (accept) {
          validate(input.value.trim());
        } else {
          render();
        }
      };
      input.addEventListener('keydown', function(event) {
        if (event.key === 'Enter') {
          event.preventDefault();
          finish(true);
        } else if (event.key === 'Escape') {
          event.preventDefault();
          finish(false);
        }
      });
      input.addEventListener('blur', function() {
        // A field removed by a new rendering (bookmarks reloaded after a conflict) must not save its stale value.
        finish(input.isConnected);
      });
      target.parentNode.replaceChild(input, target);
      input.focus();
      input.select();
    };

    var editFolder = function(id) {
      var folder = getFolder(id);
      var header = tree.querySelector('.advanced-bookmarks-folder-header[data-folder="' + CSS.escape(id) + '"]');
      if (!folder || !header) {
        return;
      }
      editInline(header.querySelector('.advanced-bookmarks-folder-name'), folder.name, '', function(value) {
        folder.name = (value || t('newFolderName')).slice(0, MAX_FOLDER_NAME_LENGTH);
        changed();
      });
    };

    var createFolder = function(parent) {
      var folder = {id: newFolderId(), name: t('newFolderName'), parent: parent, order: getSubfolders(parent).length};
      state.folders.push(folder);
      if (parent) {
        setCollapsed(parent, false);
      }
      filterInput.value = '';
      changed();
      editFolder(folder.id);
    };

    var deleteFolder = function(folder) {
      var branchIds = [folder].concat(getDescendants(folder.id)).map(function(item) { return item.id; });
      // The bookmarks of the deleted folders are kept, in the uncategorized folder.
      var uncategorized = getBookmarksOf(ROOT);
      branchIds.forEach(function(id) {
        getBookmarksOf(id).forEach(function(bookmark) {
          bookmark.folder = ROOT;
          uncategorized.push(bookmark);
        });
      });
      renumber(uncategorized);
      state.folders = state.folders.filter(function(item) { return branchIds.indexOf(item.id) < 0; });
      renumber(getSubfolders(folder.parent));
    };

    var removeBookmark = function(bookmark) {
      state.bookmarks = state.bookmarks.filter(function(item) { return item !== bookmark; });
      renumber(getBookmarksOf(bookmark.folder));
    };

    // ------------------------------------------------------------------
    // Clicks and changes
    // ------------------------------------------------------------------
    var getTargetOf = function(element) {
      var item = element.closest('.advanced-bookmarks-bookmark');
      var header = element.closest('.advanced-bookmarks-folder-header');
      var folderId = header ? header.getAttribute('data-folder') : null;
      return {
        item: item,
        bookmark: item ? getBookmark(item.getAttribute('data-page')) : null,
        folderId: folderId,
        folder: folderId ? getFolder(folderId) : null
      };
    };

    var clickActions = {
      newFolder: function() {
        createFolder(ROOT);
      },
      retry: function() {
        save();
      },
      toggle: function(target) {
        setCollapsed(target.folderId, !collapsed[folderKey(target.folderId)]);
        render();
      },
      addSubfolder: function(target) {
        createFolder(target.folder.id);
      },
      renameFolder: function(target) {
        editFolder(target.folder.id);
      },
      moveFolderUp: function(target) {
        return shift(getSubfolders(target.folder.parent), target.folder, -1);
      },
      moveFolderDown: function(target) {
        return shift(getSubfolders(target.folder.parent), target.folder, 1);
      },
      deleteFolder: function(target) {
        if (window.confirm(t('confirmDeleteFolder', target.folder.name))) {
          deleteFolder(target.folder);
          return true;
        }
      },
      rename: function(target) {
        var bookmark = target.bookmark;
        editInline(target.item.querySelector('.advanced-bookmarks-link'), bookmark.label, t('labelPlaceholder'),
          function(value) {
            bookmark.label = value.slice(0, MAX_LABEL_LENGTH);
            changed();
          });
      },
      moveUp: function(target) {
        return shift(getBookmarksOf(target.bookmark.folder), target.bookmark, -1);
      },
      moveDown: function(target) {
        return shift(getBookmarksOf(target.bookmark.folder), target.bookmark, 1);
      },
      remove: function(target) {
        if (window.confirm(t('confirmRemove', getText(target.bookmark)))) {
          removeBookmark(target.bookmark);
          return true;
        }
      }
    };
    // The actions that need a folder or a bookmark; the others apply to the whole tree.
    var folderActions = ['addSubfolder', 'renameFolder', 'moveFolderUp', 'moveFolderDown', 'deleteFolder'];
    var bookmarkActions = ['rename', 'moveUp', 'moveDown', 'remove'];

    container.addEventListener('click', function(event) {
      var button = event.target.closest('button[data-action]');
      var action = button && button.getAttribute('data-action');
      if (!button || !container.contains(button) || !clickActions.hasOwnProperty(action)) {
        return;
      }
      var target = getTargetOf(button);
      if (folderActions.indexOf(action) >= 0 && !target.folder ||
          bookmarkActions.indexOf(action) >= 0 && !target.bookmark) {
        return;
      }
      // An action returns true when it modified the bookmarks and they must be saved.
      if (clickActions[action](target) === true) {
        changed();
      }
    });

    container.addEventListener('change', function(event) {
      var element = event.target;
      var action = element.getAttribute('data-action');
      var target = getTargetOf(element);
      if (action === 'panel' && target.bookmark) {
        if (element.checked && countPanelBookmarks() >= panelLimit) {
          element.checked = false;
          notify(t('panelLimitReached', panelLimit), 'warning');
          return;
        }
        target.bookmark.panel = element.checked;
        changed();
      } else if (action === 'moveBookmark' && target.bookmark && element.value !== NO_MOVE) {
        moveBookmark(target.bookmark, element.value);
        changed();
      } else if (action === 'moveFolder' && target.folder && element.value !== NO_MOVE) {
        if (moveFolder(target.folder, element.value)) {
          setCollapsed(element.value, false);
          changed();
        } else {
          render();
        }
      }
    });

    filterInput.addEventListener('input', render);

    // ------------------------------------------------------------------
    // Drag and drop (the buttons and the "Move to" lists offer the same operations with the keyboard)
    // ------------------------------------------------------------------
    var dragged = null;
    var DROP_CLASSES = ['advanced-bookmarks-drop-before', 'advanced-bookmarks-drop-after',
      'advanced-bookmarks-drop-inside'];

    var clearDropTargets = function() {
      tree.querySelectorAll('.' + DROP_CLASSES.join(', .')).forEach(function(element) {
        DROP_CLASSES.forEach(function(className) { element.classList.remove(className); });
      });
    };

    // The drop operation allowed above the element under the pointer, or null.
    var getDrop = function(event) {
      if (!dragged) {
        return null;
      }
      var element = event.target.closest('.advanced-bookmarks-bookmark, .advanced-bookmarks-folder-header, ' +
        '.advanced-bookmarks-empty');
      if (!element || !tree.contains(element)) {
        return null;
      }
      var bounds = element.getBoundingClientRect();
      var y = (event.clientY - bounds.top) / Math.max(bounds.height, 1);

      if (element.classList.contains('advanced-bookmarks-bookmark')) {
        var targetBookmark = getBookmark(element.getAttribute('data-page'));
        if (!dragged.bookmark || !targetBookmark || targetBookmark === dragged.bookmark) {
          return null;
        }
        return {element: element, position: y < 0.5 ? 'before' : 'after', bookmark: targetBookmark};
      }

      var folderId = element.getAttribute('data-folder');
      if (dragged.bookmark) {
        return {element: element, position: 'inside', folder: folderId};
      }
      if (folderId === ROOT || isInBranch(folderId, dragged.folder.id)) {
        return null;
      }
      if (element.classList.contains('advanced-bookmarks-empty')) {
        return {element: element, position: 'inside', folder: folderId};
      }
      return {element: element, position: y < 0.25 ? 'before' : (y > 0.75 ? 'after' : 'inside'), folder: folderId};
    };

    tree.addEventListener('dragstart', function(event) {
      var source = event.target.closest && event.target.closest('[draggable="true"]');
      if (!source) {
        return;
      }
      if (source.classList.contains('advanced-bookmarks-bookmark')) {
        dragged = {bookmark: getBookmark(source.getAttribute('data-page'))};
      } else {
        dragged = {folder: getFolder(source.getAttribute('data-folder'))};
      }
      if (!dragged.bookmark && !dragged.folder) {
        dragged = null;
        return;
      }
      event.dataTransfer.effectAllowed = 'move';
      try {
        event.dataTransfer.setData('text/plain', '');
      } catch (e) {
        // Some browsers refuse custom data: dragging works without it.
      }
      var item = source.classList.contains('advanced-bookmarks-folder-header') ? source.parentNode : source;
      item.classList.add('advanced-bookmarks-dragged');
    });

    tree.addEventListener('dragover', function(event) {
      var drop = getDrop(event);
      clearDropTargets();
      if (drop) {
        event.preventDefault();
        event.dataTransfer.dropEffect = 'move';
        drop.element.classList.add('advanced-bookmarks-drop-' + drop.position);
      }
    });

    tree.addEventListener('dragleave', function(event) {
      if (!tree.contains(event.relatedTarget)) {
        clearDropTargets();
      }
    });

    tree.addEventListener('drop', function(event) {
      var drop = getDrop(event);
      clearDropTargets();
      if (!drop) {
        return;
      }
      event.preventDefault();
      if (dragged.bookmark) {
        if (drop.bookmark) {
          var siblings = getBookmarksOf(drop.bookmark.folder).filter(function(item) {
            return item !== dragged.bookmark;
          });
          var index = siblings.indexOf(drop.bookmark) + (drop.position === 'after' ? 1 : 0);
          moveBookmark(dragged.bookmark, drop.bookmark.folder, index);
        } else {
          moveBookmark(dragged.bookmark, drop.folder);
          setCollapsed(drop.folder, false);
        }
      } else if (drop.position === 'inside') {
        moveFolder(dragged.folder, drop.folder);
        setCollapsed(drop.folder, false);
      } else {
        var reference = getFolder(drop.folder);
        var sisters = getSubfolders(reference.parent).filter(function(item) { return item !== dragged.folder; });
        var position = sisters.indexOf(reference) + (drop.position === 'after' ? 1 : 0);
        moveFolder(dragged.folder, reference.parent, position);
      }
      dragged = null;
      changed();
    });

    tree.addEventListener('dragend', function() {
      dragged = null;
      clearDropTargets();
      tree.querySelectorAll('.advanced-bookmarks-dragged').forEach(function(element) {
        element.classList.remove('advanced-bookmarks-dragged');
      });
    });

    // ------------------------------------------------------------------
    // Start
    // ------------------------------------------------------------------
    container.textContent = '';
    if (container.getAttribute('data-admin') === 'true') {
      container.appendChild(createUserPicker(container));
    }
    container.appendChild(toolbar);
    container.appendChild(tree);
    showStatus(t('loading'), 'progress');
    load().then(function() {
      showStatus('', '');
    }).catch(function(error) {
      showStatus(t('loadFailed') + ' ' + error.message, 'error');
    });
  };

  $(function() {
    document.querySelectorAll('.advanced-bookmarks-manager').forEach(function(container) {
      // The macro may be displayed twice on a page (preview, included page): initialize each container once.
      if (!container.hasAttribute('data-initialized')) {
        container.setAttribute('data-initialized', 'true');
        initManager(container);
      }
    });
  });
});
