/*
* The Application Server Side Events Global Variables
 */
window.Application.Sse = window.Application.Sse || {};

/**
 * A constant for the end of data marker in Server-Sent Events (SSE).
 * @type {string}
 */
const SSE_END_OF_DATA = "$END_OF_DATA$";
// SSE connection broke, browser is auto-retrying
const SSE_READY_STATE_CONNECTING = 0;
// SSE connection was open but broke
const SSE_READY_STATE_OPEN = 1;
const SSE_READY_STATE_CLOSED = 2;

/**
 * Starts a Server-Sent Events (SSE) connection.
 *
 * @param {String} path an optional path to add to the base URI for the SSE connection
 * @param {Function} callback a callback function to receive the events from the server
 * @param {Object} [params] an optional list of parameters
 * @param {Object} [options] an optional object, to control how parameters are calculated *
 */
Application.Sse.start = function (path, callback, params, options) {
    if (!Utils.isFunction(callback)) {
        throw new Error("A callback function is required to handle the events from the server");
    }
    params.applicationId = Application.getId();
    let uri = Application.getUri(path, params, options);
    const events = new EventSource(uri, {});
    events.onmessage = (event) => {
        let data = event.data;
        if (SSE_END_OF_DATA === data) {
            Logger.debug("Received end of data from server");
            events.close();
        } else {
            Logger.debug("Received data from server, data: " + data);
            callback.call(callback, data, event);
        }
    };
    this.events = this.events || [];
    this.events.push(events);
    events.addEventListener("error-event", (event) => {
        Logger.warn("Received a server side failure from SSE connection '" + event.target.url + "', error: " + event.data);
    });
    events.onopen = (event) => {
        Logger.debug("SSE connection has been established to '" + event.target.url + "'");
    };
    events.onerror = (event) => {
        if (event.readyState === SSE_READY_STATE_CONNECTING) {
            Logger.debug("SSE connection for '" + event.target.url + "' is reconnecting");
        } else {
            Logger.debug("Received a failure from '" + event.target.url + "', state: " + event.readyState);
        }
    };
}

/**
 * Changes whether SSE is enabled or not.
 *
 * @param {boolean} enabled true if SSE is enabled, false otherwise
 */
Application.Sse.setEnabled = function (enabled) {
    let prevEnabled = this.isEnabled();
    Application.Sse.enabled = enabled;
    if (!prevEnabled && this.isEnabled()) {
        this.startDefault();
    } else if (prevEnabled && !this.isEnabled()) {
        // how do we stop?
    }
}

/**
 * Returns whether the SSE channel is enabled or not.
 *
 * @return {boolean} the true if SSE is enabled, false otherwise
 */
Application.Sse.isEnabled = function () {
    return Application.Sse.enabled !== false;
}

/**
 * Initialize the SSE channel.
 */
Application.Sse.initialize = function () {
    Application.Sse.setEnabled(Application.isAuthenticated());
    Logger.info("SSE connection is " + (Application.Sse.isEnabled() ? "enabled" : "disabled"));
}

/**
 * Starts the SSE channel.
 *
 * The default SSE channel is started only if the current application has an authenticated context. However,
 * the application can decide to start the SSE based on other conditions.
 */
Application.Sse.startDefault = function () {
    Logger.info("Starting SSE connection");
    Application.Sse.start("/event/out", function (data, event) {
        let json = JSON.parse(data);
        let name = json.name;
        delete json.name;
        delete json.application;
        if ("close" === name) {
            Logger.debug("SSE channel is being closed by the server");
        } else {
            Logger.debug("Received SSE event '" + name + "', data: " + data);
            Application.fire(name, json);
        }
    }, {}, {self: false});
}

Application.bind("start", Application.Sse.initialize);
