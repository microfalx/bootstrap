/*
* The Application Global Variables
 */
window.Application = window.Application || {};
window.Application.MODAL_Z_INDEX = 1050;

/**
 * Default params for ajax requests.
 */
const APP_AJAX_DEFAULT_OPTIONS = {self: true, params: false};
const APP_AJAX_DEFAULT_TIMEOUT = 120 * 1000;
const APP_AJAX_PING_DEFAULT_INTERVAL = 30000;
const APP_AJAX_PING_CONNECTIVITY_THRESHOLD = 5;
const APP_AJAX_PING_SESSION_THRESHOLD = 2;
const APP_AJAX_MASK_NETWORK_MESSAGE = "<p style='font-size: 16px; font-weight: bold; padding-bottom: 10px;'>Server Communication Failure</p><p>Server stopped or communication with the server is not possible due to network failure.</p>";
const APP_AJAX_MASK_AUTH_MESSAGE = "<p style='font-size: 16px; font-weight: bold; padding-bottom: 10px;'>Application Session</p><p>Application session was lost, please <a href='/login'>login</a> again.</p>";
const APP_AJAX_MASK_LOGOUT_MESSAGE = "<p style='font-size: 16px; font-weight: bold; padding-bottom: 10px;'>Application Session</p><p>Logging out and redirect to login page.</p>";

/**
 * Returns the identifier of the current application.
 *
 * @return {string} the application ID
 */
Application.getId = function () {
    if (Utils.isEmpty(APP_ID)) throw new Error("Application ID is not defined");
    return APP_ID;
}

/**
 * Returns the path of the current request.
 *
 * @return {string} the application root path
 */
Application.getPath = function () {
    return APP_REQUEST_PATH || "/";
}

/**
 * Takes a collection of parameters and creates an object with all query parameters.
 *
 * @param {Object} [params] a collection of parameters to override/extend the request parameters
 */
Application.getQuery = function (params) {
    let requestParams = $.extend({}, APP_REQUEST_QUERY || null);
    requestParams = $.extend(requestParams, params);
    return requestParams;
}

/**
 * Returns an application URI.
 *
 * @param {String} path an optional path to add to the base URI
 * @param {Object} [params] an optional list of parameters
 * @param {Object} [options] an optional object, to control how parameters are calculated
 * @param {Boolean} [options.self=true] an optional boolean, to calculate the URI to the current page (within the same resource)
 * @param {Boolean} [options.params=false] an optional boolean, to include the parameters in the URI
 */
Application.getUri = function (path, params, options) {
    options = options || APP_AJAX_DEFAULT_OPTIONS;
    options.self = Utils.isDefined(options.self) ? options.self : true;
    options.params = Utils.isDefined(options.params) ? options.params : false;
    params = options.params ? this.getQuery(params) : params;
    let uri = options.self ? this.getPath() : "/";
    if (path) {
        if (!uri.endsWith("/")) uri += "/";
        if (path.startsWith("/")) path = path.substring(1);
        uri += path;
    }
    if (Utils.isNotEmpty(params)) uri += "?" + $.param(params);
    Logger.debug("Resolve URI for path '" + path + "', params '" + Utils.toString(params) + "', uri '" + uri + "'");
    return uri;
}

/**
 * Opens an application page, using the same path (or a sub-path) as the current page and possible
 * additional parameters.
 *
 * @param {String} path an optional path to add to the base URI
 * @param {Object} params the new parameters (overrides)
 */
Application.openSelf = function (path, params) {
    let uri = this.getUri(path, params, {self: true, params: true});
    Logger.info("Open '" + uri + "'");
    window.location.href = uri;
}

/**
 * Takes a collection of parameters and opens a page at requested path, outside the current page.
 *
 * @param {Object} params the new parameters
 * @param {String} [path] an optional path to add to the base URI
 * @param {Boolean} [newWindow=false] an optional flag, true to control if the URI is opened in a new window, false otherwise
 */
Application.open = function (path, params, newWindow) {
    let uri = this.getUri(path, params, {self: false});
    newWindow = Utils.defaultIfNotDefined(newWindow, false);
    Logger.info("Open '" + uri + "', new window '" + newWindow + "'");
    window.open(uri, newWindow ? "_blank" : "");
}

/**
 * Returns whether the application is ready.
 *
 * @returns true if ready, false otherwise
 */
Application.isReady = function () {
    return true;
}

/**
 * Returns whether the application (current page) is authenticated (has a security context).
 *
 * @returns true if authenticated, false otherwise
 */
Application.isAuthenticated = function () {
    return APP_AUTHENTICATED || false;
}

/**
 * Reloads the current page.
 */
Application.reload = function () {
    this.openSelf("", {});
}

/**
 * Goes to the home page.
 */
Application.home = function () {
    this.open("", {});
}

/**
 * Displays the about box.
 */
Application.about = function () {
    Application.get("/help/about", {}, function (data) {
        Application.loadModal("help-about", data);
    }, {self: false});
}

/**
 * Logs out the current session.
 */
Application.logout = function () {
    let me = this;
    let messageElement = $("<div>", {
        "class": "app-mask",
        "html": APP_AJAX_MASK_LOGOUT_MESSAGE
    });
    this.mask(null, {
        size: false,
        image: false,
        custom: messageElement
    });
    Utils.defer(function () {
        me.post("/logout", {}, function () {
            me.home();
        }, {self: false});
    }, 1000)

}

/**
 * Executes an AJAX GET request.
 *
 * @param {String} path the path
 * @param {Object} params the parameters
 * @param {Function} callback the callback to be called with the successful response
 * @param {Object} [options] an optional object, to control how parameters are calculated
 * @param {Boolean} [options.self=true] an optional boolean, to calculate the URI relative to the current page (self)
 * @param {Boolean} [options.params=false] an optional boolean, to include the parameters in the current URI
 * @param {String} [options.dataType=text] an optional string, to provide a data type for the response
 * @param {Boolean} [options.mask] an optional DOM selector, which will be masked while the request is running
 * @param {Boolean} [options.error] an optional function, to be called if the request fails
 * @param {Boolean} [options.complete] an optional function, to be called when the request ends (successful or not)
 */
Application.get = function (path, params, callback, options) {
    Application.ajax('GET', path, params, callback, options);
}

/**
 * Executes an AJAX POST request.
 *
 * @param {String} path the path
 * @param {Object} params the parameters
 * @param {Function} callback the callback to be called with the successful response
 * @param {Object} [options] an optional object, to control how parameters are calculated
 * @param {Boolean} [options.self=true] an optional boolean, to calculate the URI relative to the current page (self)
 * @param {Boolean} [options.params=false] an optional boolean, to include the parameters in the current URI
 * @param {Boolean} [options.dataType=text] an optional string, to provide a data type for the response
 * @param {Boolean} [options.contentType] the content type of the request, defaults to "application/x-www-form-urlencoded; charset=UTF-8" for a POST
 * @param {Boolean} [options.mask] an optional DOM selector, which will be masked while the request is running
 * @param {Boolean} [options.error] an optional function, to be called if the request fails
 * @param {Boolean} [options.complete] an optional function, to be called when the request ends (successful or not)
 */
Application.post = function (path, params, callback, options) {
    Application.ajax('POST', path, params, callback, options);
}

/**
 * Executes an AJAX DELETE request.
 *
 * @param {String} path the path
 * @param {Object} params the parameters
 * @param {Function} callback the callback to be called with the successful response
 * @param {Object} [options] an optional object, to control how parameters are calculated
 * @param {Boolean} [options.self=true] an optional boolean, to calculate the URI relative to the current page (self)
 * @param {Boolean} [options.params=false] an optional boolean, to include the parameters in the current URI
 * @param {Boolean} [options.dataType=text] an optional string, to provide a data type for the response
 * @param {Boolean} [options.mask] an optional DOM selector, which will be masked while the request is running
 * @param {Boolean} [options.error] an optional function, to be called if the request fails
 * @param {Boolean} [options.complete] an optional function, to be called when the request ends (successful or not)
 */
Application.delete = function (path, params, callback, options) {
    Application.ajax('DELETE', path, params, callback, options);
}

/**
 * Executes an AJAX request.
 *
 * @param {String} type the HTTP verb
 * @param {String} path the path
 * @param {Object} params the parameters
 * @param {Function} callback the callback to be called with the successful response
 * @param {Object} [options] an optional object, to control how parameters are calculated
 * @param {Boolean} [options.self=true] an optional boolean, to calculate the URI relative to the current page (self)
 * @param {Boolean} [options.params=false] an optional boolean, to include the parameters in the current URI
 * @param {Boolean} [options.dataType=text] an optional string, to provide a data type for the response
 * @param {Boolean} [options.data=text] the body of a POST
 * @param {Boolean} [options.contentType] the content type of the request, defaults to "application/x-www-form-urlencoded; charset=UTF-8" for a POST
 * @param {String} [options.mask] an optional DOM selector, which will be masked while the request is running
 * @param {String} [options.maskMessage] an optional message to display while masking, defaults to no message
 * @param {Function} [options.error] an optional function, to be called if the request fails
 * @param {Function} [options.complete] an optional function, to be called when the request ends (successful or not)
 * @param {Boolean} [options.background] true if the request is a background request, false if triggered by users
 * @param {Number} [options.timeout] an optional timeout in milliseconds, defaults to APP_AJAX_DEFAULT_TIMEOUT
 */
Application.ajax = function (type, path, params, callback, options) {
    const me = this;
    options = options || {};
    options.self = Utils.isDefined(options.self) ? options.self : true;
    options.params = Utils.isDefined(options.params) ? options.params : false;
    options.error = Utils.defaultIfNotDefinedOrNull(options.error, function () {
    });
    options.background = Utils.defaultIfNotDefinedOrNull(options.background, false);
    options.dataType = Utils.defaultIfNotDefinedOrNull(options.dataType, "text");
    params = options.params ? this.getQuery(params) : params;
    options.params = false;
    type = Utils.defaultIfNotDefinedOrNull(type, "GET");
    let timeout = Utils.defaultIfNotDefinedOrNull(options.timeout, APP_AJAX_DEFAULT_TIMEOUT);
    let uri = this.getUri(path, {}, options);
    let data = null;
    if (type === 'POST' || type === 'PUT') {
        data = Utils.defaultIfNotDefinedOrNull(options.data, params);
    } else {
        data = params;
    }

    if (options.contentType === 'json') {
        options.contentType = 'application/json; charset=UTF-8';
        data = data ? JSON.stringify(data) : null;
    }
    Logger.log(options.background ? "info" : "trace", "Ajax Request: " + uri + ", params: " + Utils.toString(params) + ", data type " + options.dataType);
    if (options.mask) me.mask(options.mask, options.maskMessage);
    let headers = me.getHeaders();
    $.ajax({
        url: uri,
        type: type,
        data: data,
        dataType: options.dataType,
        contentType: Utils.defaultIfNotDefinedOrNull(options.contentType, "application/x-www-form-urlencoded; charset=UTF-8"),
        timeout: timeout,
        headers: headers,
        success: function (output, status, xhr) {
            if (options.complete) options.complete.apply(this, arguments);
            if (options.mask) me.unmask(options.mask);
            callback.apply(this, [output, status, xhr]);
        },
        error: function (xhr, status, error) {
            if (options.error) options.error.apply(this, arguments);
            if (options.complete) options.complete.apply(this, arguments);
            if (options.mask) me.unmask(options.mask);
        }
    });
    let shouldRunAnalytics = !uri.startsWith("/ping");
    if (shouldRunAnalytics) {
        this.analytics("ajax", {
            page_title: window.title,
            page_location: window.location.href,
            ajax_location: uri,
            page_path: APP_REQUEST_PATH
        })
    }
}

/**
 * Returns an object with application required headers.
 */
Application.getHeaders = function () {
    let headers = {
        "X-Application-Id": Application.getId(),
        "X-TimeZone": Application.getTimezoneOffset()
    };
    if (APP_CSRF !== false) headers[APP_CSRF.headerName] = APP_CSRF.token;
    let localStorage = {};
    LocalStorage.copy(localStorage);
    headers["X-Application-LocalState"] = JSON.stringify(localStorage);
    return headers;
}

/**
 * Updates the CSRF token in the given parameters.
 *
 * @param {Object} params the parameters where to set the token
 */
Application.updateCsrf = function (params) {
    if (APP_CSRF !== false) params[APP_CSRF.parameterName] = APP_CSRF.token;
}

/**
 * Returns the query parameter with a given name.
 *
 * @param {String} name the parameter name
 * @return {String} the value of the parameter
 */
Application.getQueryParam = function (name) {
    let url_string = location.href;
    let url = new URL(url_string);
    return url.searchParams.get(name);
}

/**
 * Returns the hash path from the current URI.
 *
 * @return {String} the path from has
 */
Application.getHashPath = function () {
    let url = this.getHashUrl();
    return url.pathname;
}

/**
 * Returns the hash parameter with a given name.
 *
 * @param {String} name the parameter name
 * @return {String} the value of the parameter
 */
Application.getHashParam = function (name) {
    let url = this.getHashUrl();
    return url.searchParams.get(name);
}

/**
 * Returns an URL made out of the hash (as path and params)
 * @return {URL} the URL
 */
Application.getHashUrl = function () {
    let hash = location.hash;
    hash = hash && (hash.charAt(0) === '#') ? hash.slice(1) : hash;
    if (!hash.startsWith("/")) hash = "/" + hash;
    return new URL("http://localhost" + hash);
}

/**
 * Saves a form.
 *
 * @param {Element|String} selector the element or selector for form
 * @param {String} [path] the path where to send the form
 * @param {Object} [params] the query parameters for the form
 * @param {Object} [options] the used to control the form
 */
Application.saveForm = function (selector, path, params, options) {
    Utils.requireNonNull(selector);
    let me = Application;
    options = options || {};
    params = params || {};
    let form = $(selector);
    if (Utils.isEmpty(path)) path = form.attr('action');
    if (Utils.isEmpty(path)) throw new Error("Path or Form.action attribute must be specified");
    let url = this.getUri(path, params, {params: false, self: options.self});
    let headers = this.getHeaders();
    let before = options.before;
    let success = options.success;
    form.ajaxSubmit({
        url: url,
        type: 'POST',
        dataType: 'json',
        headers: headers,
        beforeSubmit: function (data, form, options) {
            me.updateFormFields(form, data);
            if (before) before.apply(this, [data]);
            Logger.info("Before form submission, response " + Utils.toString(data));
        },
        error: function (jqXHR, textStatus, errorThrown) {
            let message = "An error was encountered which submitting the form to '" + url + "'";
            me.showErrorAlert("Submit", message);
            message += ", error: " + errorThrown;
            Logger.error(message);
        },
        success: function (data, textStatus, jqXHR) {
            Logger.info("Form was submitted successfully, response " + Utils.toString(data));
            if (data.success) {
                if (success) success.apply(this, [data]);
            } else {
                form.find('input').removeClass('is-invalid').tooltip("dispose");
                let errors = data.errors || {};
                if (Utils.isNotEmpty(data.message)) {
                    me.showErrorAlert("Validation", data.message);
                } else {
                    me.showErrorAlert("Validation", "Form cannot be submitted with invalid values");
                    for (let field in errors) {
                        let message = errors[field];
                        let formField = el.find("input[name='" + field + "']");
                        formField.addClass("is-invalid");
                        me.showTooltip(formField, message);
                    }
                }
            }
        }
    });
}

/**
 * Updates for data before it is sent to the server.
 *
 * @param {JQuery} el the form element
 * @param {Array} data an array with object, one property "name" with the value
 */
Application.updateFormFields = function (el, data) {
    let fieldNames = {};
    for (let tuple of data) {
        fieldNames[tuple["name"]] = true;
    }
    el.find('input').each(function (index) {
        if ($(this).attr("type") === "checkbox") {
            let name = $(this).attr("name");
            if (!fieldNames[name]) {
                data.push({name: name, value: 'off'});
            }
        }
    });
}

/**
 * Closes all open popups.
 */
Application.closePopups = function () {
    this.getPopups().forEach(function (popup) {
        popup.hide();
    });
    this.popups = [];
}

/**
 * Registers a popup, which is used to validate if the user clicks outside the popup.
 * @param {Object} element a
 */
Application.registerPopup = function (element) {
    this.getPopups().push(element);
}

/**
 * Returns the registers popups.
 *
 * @return {Object[]} the popups.
 */
Application.getPopups = function () {
    this.popups = this.popups || [];
    return this.popups;
}

/**
 * Closes last dialog.
 */
Application.closeModal = function () {
    let modal = this.getModals().pop();
    if (modal) {
        Application.MODAL_Z_INDEX -= 10;
        modal.hide();
    }
}

/**
 * Shows a dialog (modal) which contains the HTML fragment.
 *
 * @param {String} id the identifier of the modal (DOM element)
 * @param {String} content the contained of the modal (HTML, Text, etc)
 * @return {bootstrap.Modal} the modal
 */
Application.loadModal = function (id, content) {
    let me = Application;
    Utils.requireNonNull(id, "id")
    me.removeModal(id);
    let selector = '#' + id;
    $(document.body).append(content);
    let modalElement = $(selector);
    if (modalElement.length === 0) {
        throw new Error("A modal with id '" + id + "' does not exist in the DOM");
    }
    // remove the DOM element after the hide transition finishes
    modalElement.on('hidden.bs.modal', function () {
        me.removeModal(id);
    })
    let modal = new bootstrap.Modal(selector, {});
    modal.show();
    this.registerModal(id, modal);
    tippy('[data-tippy-content]');
    return modal;
}

/**
 * @private
 * Removes the model element with a given id from the DOM.
 * @param {String} id the element id
 */
Application.removeModal = function (id) {
    Utils.requireNonNull(id, "id")
    $('#' + id).remove();
}

/**
 * Registers a modal, which is used to validate if the user clicks outside the popup.
 * @param {bootstrap.Modal} modal the modal instance
 */
Application.registerModal = function (id, modal) {
    this.getModals().push(modal);
    let selector = '#' + id;
    Application.MODAL_Z_INDEX += 10;
    let modalIndex = Application.MODAL_Z_INDEX;
    let modalBackdropIndex = Application.MODAL_Z_INDEX - 1;
    // Manually bump z-index of the modal and backdrop
    $(selector).css('z-index', modalIndex);
    $('.modal-backdrop').last().css('z-index', modalBackdropIndex);
}

/**
 * Returns the registers modals.
 *
 * @return {bootstrap.Modal[]} the modals.
 */
Application.getModals = function () {
    this.modals = this.modals || [];
    return this.modals;
}

/**
 * Returns the application theme.
 *
 * @returns {*|string} a non-null string
 */
Application.getTheme = function () {
    return APP_THEME || "bootstrap";
}

/**
 * Returns the mode for the application theme (light/dark/auto).
 *
 * @returns {*|string} a non-null string
 */
Application.getThemeMode = function () {
    return APP_THEME_MODE || "auto";
}

/**
 * Returns the time zone offset.
 *
 * @return {number} offset in minutes since UTC
 */
Application.getTimezoneOffset = function () {
    return new Date().getTimezoneOffset();
}

/**
 * Copies the content to the clipboard.
 *
 * @param {String} text the text to copy
 * @param {String} [title=Copy] title the title of the post-copy notification
 */
Application.copyToClipboard = async function (text, title) {
    Utils.requireNonNull(text);
    title = Utils.defaultIfNotDefinedOrNull(title, "Copy");
    if (!document.hasFocus()) {
        Application.showWarnAlert(title, "Failed to copy text to clipboard, document is not focused");
        return;
    }
    let copied = true;
    try {
        if (navigator.clipboard && window.isSecureContext) {
            try {
                await navigator.clipboard.writeText(text);
            } catch (e) {
                Logger.info("Failed to copy text using navigator.clipboard, reason: " + e.message);
                copied = false;
            }
        }
        if (!copied) {
            const input = document.createElement("input");
            input.value = text;
            input.style.position = "fixed";
            input.style.opacity = 0;
            document.body.appendChild(input);
            input.focus();
            input.select();
            copied = document.execCommand("copy");
            document.body.removeChild(input);
        }

    } catch (e) {
        copied = false;
    }
    if (copied) {
        Application.showWarnAlert(title, "Text copied to clipboard");
    } else {
        Application.showWarnAlert(title, "Failed to copy text to clipboard, reason " + e.message);
    }
}

/**
 * Copies the content of an element to the clipboard.
 *
 * @param {String|Element} selector the DOM selector or the element to copy
 * @param {String} [title=Copy] title the title of the post-copy notification
 */
Application.copyElementToClipboard = async function (selector, title) {
    Utils.requireNonNull(selector);
    title = Utils.requireNonNull(title, "Copy");
    let text = $(selector).text();
    await this.copyToClipboard(text, title);
}

/**
 * Pings the backend to keep the session alive and detect if the server is still accessible.
 */
Application.ping = function () {
    let me = this;
    me.pingCount = (me.pingCount ?? 0) + 1;
    Application.get("/ping", {}, function (data) {
        if (me.isConnectionLost()) {
            Logger.info("Connection re-established");
            Application.Sse.setEnabled(true);
            me.unmask();
            me.pingConnectionLostCount = 0;
            me.pingSessionLostCount = 0;
            me.checkServerStatusSent = false;
        }
        let sessionLost = !data.success && data.errorCode === 5;
        if (sessionLost) {
            me.pingSessionLostCount++;
            me.checkServerStatus(APP_AJAX_PING_SESSION_THRESHOLD);
            if (me.isSessionLost()) me.showSessionLost();
        } else {
            me.pingSessionLostCount = 0;
            me.checkServerStatusSent = false;
        }
    }, {
        self: false,
        timeout: 2000,
        dataType: 'json',
        error: function (xhr) {
            if (xhr.status === 0) {
                Logger.trace("Failed to executed ping, connection lost");
                me.pingConnectionLostCount++;
                me.checkServerStatus(APP_AJAX_PING_CONNECTIVITY_THRESHOLD);
                if (me.isConnectionLost()) {
                    me.showConnectionLost();
                    Application.Sse.setEnabled(false);
                }
            }
        }
    });
}

/**
 * Returns whether the session is lost.
 * @return {boolean} true if lost, false otherwise
 */
Application.isSessionLost = function () {
    this.pingSessionLostCount = this.pingSessionLostCount || 0;
    return this.pingSessionLostCount >= APP_AJAX_PING_SESSION_THRESHOLD;
}

/**
 * Shows an application mask to inform the user that the session was lost.
 */
Application.showSessionLost = function () {
    if (!this.isAuthenticated()) return;
    let messageElement = $("<div>", {
        "class": "app-mask",
        "html": APP_AJAX_MASK_AUTH_MESSAGE
    });
    this.mask(null, {
        size: false,
        image: false,
        custom: messageElement
    });
}

/**
 * Returns whether the connection with the server is lost.
 * @return {boolean} true if lost, false otherwise
 */
Application.isConnectionLost = function () {
    this.pingConnectionLostCount = this.pingConnectionLostCount || 0;
    return this.pingConnectionLostCount >= APP_AJAX_PING_CONNECTIVITY_THRESHOLD;
}

/**
 * Shows an application mask to inform the user that the connection was lost.
 */
Application.showConnectionLost = function () {
    let messageElement = $("<div>", {
        "class": "app-mask",
        "html": APP_AJAX_MASK_NETWORK_MESSAGE
    });
    this.mask(null, {
        size: false,
        image: false,
        custom: messageElement
    });
}

/**
 * Fires a few extra pings to validate if the server is still there.
 *
 * @param {Number} pings the number of pings to execute for validation
 */
Application.checkServerStatus = function (pings) {
    if (this.checkServerStatusSent) return;
    Logger.trace("Check server status");
    let interval = 1000;
    for (let i = 1; i <= pings; i++) {
        Utils.defer(this.ping, interval, this);
        interval *= 2;
    }
    this.checkServerStatusSent = true;
}

/**
 * Masks the element with a given selector.
 *
 * Outside standard options provided by the library, passing small = true will configure the mask with
 * a small animated icon.
 *
 * @param {String} [selector] the DOM selector to mask, if not provided the whole body will be masked *
 * @param {Object} [options] additional options passed to the overlay library (see https://gasparesganga.com/labs/jquery-loading-overlay/#options-and-defaults-values)
 */
Application.mask = function (selector, options) {
    options = options || {};
    let cardClass = $('.card');
    const cardBackground = Utils.addAlpha(cardClass.css('background-color'), 0.7);
    const cardColor = cardClass.css('color');
    if (options.small) {
        options = Utils.applyIf(options, {
            fontawesome: "fa-solid fa-arrows-rotate",
            fontawesomeAnimation: 'rotate_right',
            minSize: 20,
            maxSize: 40
        });
    }
    options = Utils.applyIf(options, {
        textColor: cardColor,
        imageColor: cardColor,
        background: cardBackground
    });
    $.LoadingOverlaySetup(options);
    if (selector) {
        $(selector).LoadingOverlay("show");
    } else {
        $.LoadingOverlay("show");
    }
}

/**
 * Unmasks the element with a given selector.
 *
 * @param {String} [selector] the DOM selector to mask, if not provided the whole body will be masked
 */
Application.unmask = function (selector) {
    if (selector) {
        $(selector).LoadingOverlay("hide", true);
    } else {
        $.LoadingOverlay("hide", true);
    }
}

/**
 * Executes a given action.
 *
 * @param {String} eventOrHandler the function to be called (handler) or the event to fire
 * @param {{}} [arguments] the arguments passed to the action listener
 */
Application.action = function (eventOrHandler) {
    if (Utils.isEmpty(eventOrHandler)) throw new Error("An event or a handler/function is required");
    let args = Array.prototype.slice.call(arguments, 1);
    if (Utils.isFunction(Application[eventOrHandler])) {
        Logger.info("Invoke handler '" + eventOrHandler + "'");
        Application[eventOrHandler].apply(this, args);
    } else {
        args.unshift(eventOrHandler);
        Application.fire.apply(this, args);
    }
}

/**
 * Registers an event listener.
 *
 * @param {String} name the event name
 * @param {Function} callback the function to be called when the event it is triggered.
 */
Application.bind = function (name, callback,) {
    if (!Utils.isFunction(callback)) {
        Logger.error("Callback is not a function for event '" + name + "'");
        return
    }
    let args = Array.prototype.slice.call(arguments, 2);
    Logger.debug("Bind event " + name + ", arguments: " + Utils.toString(args));
    this.listeners = this.listeners || {};
    this.listeners[name] = this.listeners[name] || []
    this.listeners[name].push(callback);
    this.listeners_args = this.listeners_args || {};
    this.listeners_args[name] = this.listeners_args[name] || []
    this.listeners_args[name] = args;
}

/**
 * Registers an event listener.
 *
 * @param {String} name the event name
 * @param {Function} [callback] the function to be called when the event it is triggered.
 */
Application.unbind = function (name, callback) {
    Logger.debug("Bind event " + name);
    let listeners = this.getListeners(name);
    let index = listeners.indexOf(callback);
    if (index !== -1) listeners.splice(index, 1);
}

/**
 * Fires an event to all listeners.
 *
 * @param {String} name the event name
 * @param {{}} [arguments] the arguments to be passed to the event callback.
 */
Application.fire = function (name) {
    let me = this;
    let args = Array.prototype.slice.call(arguments, 1);
    let extraArgs = this.listeners_args[name] || [];
    args = args.concat(extraArgs);
    Logger.debug("Fire event " + name + ", arguments " + Utils.toString(args));
    let listeners = this.getListeners(name);
    for (const listener of listeners) {
        setTimeout(function () {
            listener.apply(me, args);
        }, 5);
    }
}

/**
 * Returns the listeners associated with an event.
 *
 * @param {String} name the name of the event
 * @return {Function[]} an array of listeners
 */
Application.getListeners = function (name) {
    this.listeners = this.listeners || {};
    this.listeners[name] = this.listeners[name] || [];
    return this.listeners[name];
}

/**
 * Initialize various global events
 */
Application.initEvents = function () {
    let me = this;
    $(document).on('click touchend', function (e) {
        let target = $(e.target);
        let located = false;
        me.getPopups().forEach(function (popup) {
            if (target.is(popup)) located = true;
        });
        if (!located) me.closePopups();
    });
}

/**
 * Initialize time zone.
 */
Application.initTimeZone = function () {
    if (Utils.isNotEmpty(APP_TIME_ZONE)) return;
    Logger.info("Send client time zone '" + Application.getTimezoneOffset() + "'");
    Application.post("settings/session/time-zone", {}, function (data) {
        // nothing to process
    }, {self: false});
}

/**
 * Pushes an event to the analytics.
 *
 * By default, it does nothing, it will be replaced if analytics is enabled.
 *
 * @param event the event name
 * @param options the options
 */
Application.analytics = function (event, options) {
    // empty on purpose
}

/**
 * Initialize the theme.
 */
Application.initTheme = function () {
    function updateTheme() {
        const colorMode = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
        document.documentElement.setAttribute("data-bs-theme", colorMode);
    }

    // Set theme on load
    updateTheme();
    // Update theme when the preferred scheme changes
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', updateTheme);
}

/**
 * Initializes the application configuration.
 */
Application.initConfiguration = function () {
    Configuration.initialized(APP_CONFIGURATION ?? {});
}

/**
 * Initializes the application
 */
Application.initialize = function () {
    Logger.debug("Initialize application, request path '" + this.getPath() + "', request arguments '" + Utils.toString(this.getQuery()) + "'");
    this.initEvents();
    this.initTimeZone();
    LocalStorage.init();
}

/**
 * Starts the application.
 */
Application.start = function () {
    this.initConfiguration();
    Logger.debug("Start application");
    this.fire("start");
    User.start();
    Utils.schedule(this.ping, APP_AJAX_PING_DEFAULT_INTERVAL, this);
}

// initialize application
Application.initialize();


