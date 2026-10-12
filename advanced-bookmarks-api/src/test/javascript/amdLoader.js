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
 * Minimal RequireJS replacement for the unit tests: loads scripts of Advanced Bookmarks, records the modules they
 * define and runs their require() callbacks, resolving the dependencies first from the doubles given by the test,
 * then from the modules defined by the loaded scripts.
 */
const path = require('path');

const SCRIPTS_DIRECTORY = path.resolve(__dirname, '../../main/resources/advancedbookmarks');

/**
 * Load scripts, in the given order, as RequireJS would.
 *
 * @param fileNames the names of the scripts, relative to the advancedbookmarks resource directory
 * @param doubles the test doubles of the external modules (such as "jquery" or "xwiki-meta"), by module name
 * @return an object whose get(name) method returns the module with the given name
 */
function loadScripts(fileNames, doubles) {
  const definitions = {};
  const instances = Object.assign({}, doubles);

  const get = function(name) {
    if (!(name in instances)) {
      const definition = definitions[name];
      if (!definition) {
        throw new Error('Unknown module: ' + name);
      }
      instances[name] = typeof definition.factory === 'function'
        ? definition.factory.apply(null, definition.dependencies.map(get)) : definition.factory;
    }
    return instances[name];
  };

  const define = function(name, dependencies, factory) {
    // define(name, value) declares a module without dependencies, such as a list of translation keys.
    definitions[name] = factory === undefined ? {dependencies: [], factory: dependencies}
      : {dependencies: dependencies, factory: factory};
  };

  const requireModules = function(dependencies, callback) {
    callback.apply(null, dependencies.map(get));
  };

  fileNames.forEach(function(fileName) {
    require(path.join(SCRIPTS_DIRECTORY, fileName))(define, requireModules);
  });
  return {get: get};
}

module.exports = {loadScripts: loadScripts};
