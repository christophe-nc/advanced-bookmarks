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
 * Helpers shared by the scripts of Advanced Bookmarks: calls to the JSON service, element creation, notifications and
 * file downloads. This script must be loaded before the scripts that require it.
 *
 * Security: texts are inserted with textContent or as attribute values, never as HTML.
 */
define('advanced-bookmarks-common', ['xwiki-meta'], function(xm) {
  'use strict';

  var serviceReference = XWiki.Model.resolve('AdvancedBookmarks.Code.Service', XWiki.EntityType.DOCUMENT);
  var serviceURL = new XWiki.Document(serviceReference).getURL('get');

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

  var notify = function(message, type) {
    new XWiki.widgets.Notification(message, type || 'done');
  };

  /**
   * Call the JSON service (AdvancedBookmarks.Code.Service). Writes are sent in POST with the CSRF token; a FormData
   * is sent as multipart/form-data, which is how files larger than the maximum POST size of the server are sent.
   * The returned promise is rejected with an Error whose message is the translated error code of the service, and
   * whose "status" is the HTTP status.
   *
   * @param l10n the translations of the calling script, which include the keys of errorKeys
   * @param parameters the parameters of the call, as an object or, for a write, as a FormData
   * @param post true for a write
   */
  var callService = function(l10n, parameters, post) {
    var options = {credentials: 'same-origin', headers: {'Accept': 'application/json'}};
    var url = serviceURL;
    if (post) {
      var body = parameters instanceof FormData ? parameters : new URLSearchParams(parameters);
      body.append('form_token', xm.form_token);
      options.method = 'POST';
      options.body = body;
    } else {
      url += (url.indexOf('?') < 0 ? '?' : '&') + new URLSearchParams(parameters).toString();
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

  /**
   * Let the browser save a text as a JSON file.
   */
  var downloadFile = function(content, fileName) {
    var url = URL.createObjectURL(new Blob([content], {type: 'application/json'}));
    var link = createElement('a', {href: url, download: fileName, className: 'hidden'});
    document.body.appendChild(link);
    link.click();
    link.remove();
    // Revoked later rather than immediately, since some browsers start the download asynchronously.
    window.setTimeout(function() {
      URL.revokeObjectURL(url);
    }, 60000);
  };

  /**
   * @return the current date, as used in the names of export files (2026-10-10)
   */
  var getDateForFileName = function() {
    return new Date().toISOString().slice(0, 10);
  };

  return {
    // The error codes of the service, which the translations of the calling scripts must include.
    errorKeys: [
      'error.forbidden',
      'error.invalidToken',
      'error.methodNotAllowed',
      'error.invalid',
      'error.conflict',
      'error.storageError',
      'error.unavailable',
      'error.unknownOperation',
      'error.network'
    ],
    createElement: createElement,
    notify: notify,
    callService: callService,
    downloadFile: downloadFile,
    getDateForFileName: getDateForFileName
  };
});
