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
 * Jest transformer for the scripts of Advanced Bookmarks. These scripts are not CommonJS modules: they call the global
 * define() and require() functions of RequireJS, but Jest gives each module its own CommonJS require(). Each script is
 * therefore wrapped in a function that receives define() and require() as parameters, so that the tests can provide
 * their own. The wrapper is added on the first line so that the line numbers of the coverage report stay right.
 */
module.exports = {
  process(sourceText) {
    return {code: 'module.exports = function(define, require) {' + sourceText + '\n};'};
  }
};
