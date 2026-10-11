/**
 * Constants for alert types
 * @type {string}
 */
const ALERT_TYPE_INFO = "INFO";
const ALERT_TYPE_WARN = "WARN";
const ALERT_TYPE_ERROR = "ERROR";

/**
 * Shows an informational message.
 *
 * @param {String} title the title
 * @param {String} message the message to display, it can contain HTML tags
 */
Application.showInfoAlert = function (title, message) {
    Application.showAlert(title, message, ALERT_TYPE_INFO);
}

/**
 * Shows a warning message.
 *
 * @param {String} title the title
 * @param {String} message the message to display, it can contain HTML tags
 */
Application.showWarnAlert = function (title, message) {
    Application.showAlert(title, message, ALERT_TYPE_WARN);
}

/**
 * Shows an error message.
 *
 * @param {String} title the title
 * @param {String} message the message to display, it can contain HTML tags
 */
Application.showErrorAlert = function (title, message) {
    Application.errors = Application.errors || [];
    Application.errors.push({
        title: title,
        message: message,
    })
    Application.showAlert(title, message, ALERT_TYPE_ERROR);
}

/**
 * Returns an array with application errors.
 *
 * @return {Object[]} the errors
 */
Application.getErrors = function () {
    return this.errors || [];
}

/**
 * Returns whether the application has (AJAX) errors.
 *
 * @return {boolean} true if it has errors, false otherwise
 */
Application.hasErrors = function () {
    return this.getErrors().length > 0;
}

/**
 * Asks the user a question and invokes the callback with the answer
 * @param {String} title the title of the message box
 * @param {String} message the question to be asked
 * @param {Function} callback the callback to call with the answer
 */
Application.question = function (title, message, callback) {
    iziToast.question({
        timeout: 10000,
        close: false,
        overlay: true,
        displayMode: 'once',
        id: 'question',
        zindex: 1000000,
        title: title,
        message: message,
        position: 'center',
        buttons: [
            ['<button><b>Yes</b></button>', function (instance, toast) {
                instance.hide({transitionOut: 'fadeOut'}, toast, 'button');
                callback.call(this);
            }, true],
            ['<button>No</button>', function (instance, toast) {
                instance.hide({transitionOut: 'fadeOut'}, toast, 'button');
            }],
        ]
    });
}

/**
 * Shows an alert.
 *
 * @param {String} title the title
 * @param {String} message the message to display, it can contain HTML tags
 * @param {String|Object} type the type of alert or an object with settings for the toast component.
 */
Application.showAlert = function (title, message, type) {
    let options;
    if (Utils.isObject(type)) {
        options = type;
        type = options.type || ALERT_TYPE_INFO;
    } else {
        options = {};
    }
    type = type || "INFO";
    let icon = "fa-solid fa-circle-info";
    let color = "green";
    switch (type) {
        case 'WARN' :
            icon = "fa-solid fa-triangle-exclamation";
            color = "yellow";
            break
        case 'ERROR' :
            icon = "fa-solid fa-circle-xmark";
            color = "red";
            break
    }
    message = Utils.isEmpty(message) ? "N/A" : message;
    options = Utils.applyIf(options, {
        title: title,
        titleSize: 14,
        message: message,
        icon: icon,
        close: true,
        timeout: 5000,
        maxWidth: 400,
        position: 'topRight',
        color: color
    });
    iziToast.show(options);
}

/**
 * Shows a tooltip on an element.
 *
 * @param {Object|String} element the element reference
 * @param {String} message the message to display
 * @param {Object} [options] the options used to display the tooltip
 */
Application.showTooltip = function (element, message, options) {
    if ($(element).length === 0) {
        Logger.error("An element with identifier '" + element + "' does not exist");
    } else {
        options = Utils.applyIf(options || {}, {
            placement: 'right',
            trigger: 'manual',
            delay: {"show": 0, "hide": 2000},
            customClass: 'alert-danger'
        });
        options.title = message;
        let tooltip = new bootstrap.Tooltip($(element), options);
        tooltip.show();
    }
}

/**
 * Hides a tooltip on an element.
 *
 * @param {Object|String} element the element reference
 */
Application.hideTooltip = function (element) {
    if ($(element).length === 0) {
        Logger.error("An element with identifier '" + element + "' does not exist");
    } else {
        $(element).tooltip("dispose");
    }
}