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
 * Test doubles of the XWiki JavaScript API, shared by the unit tests.
 */

const SERVICE_URL = '/bin/get/AdvancedBookmarks/Code/Service';

/**
 * Install a double of the global XWiki object.
 *
 * @param serviceURL the URL returned for the service page, SERVICE_URL by default
 * @return the double
 */
function installXWiki(serviceURL) {
  const xwiki = {
    EntityType: {DOCUMENT: 'document'},
    Model: {resolve: jest.fn((reference, type) => ({reference: reference, type: type}))},
    Document: jest.fn(function(reference) {
      this.reference = reference;
      this.getURL = jest.fn(() => serviceURL || SERVICE_URL);
    }),
    widgets: {Notification: jest.fn()}
  };
  window.XWiki = xwiki;
  return xwiki;
}

/**
 * @return a double of the translations of a script, which returns "translated:" followed by the key and its
 *     parameters, separated by colons
 */
function createL10n() {
  return {get: jest.fn((...keyAndParameters) => 'translated:' + keyAndParameters.join(':'))};
}

/**
 * @param status the HTTP status
 * @param body the JSON body, or an Error to simulate a body that is not JSON
 * @return a double of a fetch() response
 */
function createResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status: status,
    json: jest.fn(() => body instanceof Error ? Promise.reject(body) : Promise.resolve(body))
  };
}

/**
 * @return a promise resolved once the pending promise callbacks, such as those of a service call, have run
 */
function flushPromises() {
  return new Promise(resolve => setTimeout(resolve, 0));
}

module.exports = {
  SERVICE_URL: SERVICE_URL,
  installXWiki: installXWiki,
  createL10n: createL10n,
  createResponse: createResponse,
  flushPromises: flushPromises
};
