/**
 * Run a function received as parameter in a specified context.
 *
 * @example
 * var result = contextLib.runWith({
 *     branch: 'draft',
 *     user: 'su'
 *   },
 *   callback
 * });
 *
 * @param {object} context JSON parameters.
 * @param {string} context.branch Name of the branch to execute the callback in.
 * @param {string} context.user Name of user to execute the callback with.
 * @param {function} callback Function to execute.
 * @returns {object} Result of the function execution.
 */
exports.runWith = function (context, callback) {
    var bean = __.newBean('com.enonic.xp.lib.context.RunWithHandler');

    if (context.branch) {
        bean.setBranch(context.branch);
    }
    if (context.user) {
        bean.setUser(context.user);
    }
    return bean.run(callback);
};