/*
* The LocalStorage Global Variables
 */
window.LocalStorage = window.LocalStorage || {};

/**
 * Returns the value associated with the key from the browser's local storage.
 *
 * @param {string} key - The key to retrieve the value for
 * @return {Object} the value associated with the key
 */
LocalStorage.get = function (key) {
    let value = localStorage.getItem(key);
    return value ? JSON.parse(value) : null;
}

/**
 * Stores the value associated with the key in the browser's local storage.
 *
 * @param {string} key - The key to store the value for
 * @param {Object} object - The value to store
 * @return {object} the previous value associated with the key
 */
LocalStorage.set = function (key, object) {
    let value = LocalStorage.get(key);
    if (object) {
        let value = JSON.stringify(object);
        Logger.info("Set stateful key '" + key + "' with value: '" + value + "'");
        localStorage.setItem(key, value);
    } else {
        localStorage.removeItem(key);
    }
    return value;
}

/**
 * Removes the value associated with the key from the browser's local storage.
 *
 * @param {string} key - The key to remove the value for
 * @return {Object} the value associated with the key
 */
LocalStorage.remove = function (key) {
    let value = LocalStorage.get(key);
    Logger.info("Remove stateful key '" + key + "' with value: '" + value + "'");
    localStorage.removeItem(key);
    return value;
}

/**
 * Clears all key-value pairs from the browser's local storage.
 */
LocalStorage.clear = function () {
    localStorage.clear();
}

/**
 * Registers a key from the local storage to be tracked as server side state.
 * @param {string} key - The key to register
 */
LocalStorage.register = function (key) {
    this.stats = this.stats || {};
    Logger.info("Register stateful key '" + key + "'");
    this.stats[key] = this.stats[key] || 0;
}

/**
 * Copies the state of the local storage to the provided data object.
 *
 * @param {object} data - The object to copy the local storage state into
 */
LocalStorage.copy = function (data) {
    this.stats = this.stats || {};
    let count = Object.keys(this.stats).length;
    Logger.info("Copy state with " + count + " key-value pairs.");
    for (const [key, value] of Object.entries(this.stats)) {
        data[key] = this.get(key);
    }
}

/**
 * Initializes the local storage.
 */
LocalStorage.init = function () {
    if (!APP_LOCAL_STORAGE) return;
    let count = Object.keys(APP_LOCAL_STORAGE).length;
    Logger.info("Initialize local storage with " + count + " key-value pairs.");
    for (const [key, value] of Object.entries(APP_LOCAL_STORAGE)) {
        LocalStorage.set(key, value);
    }
}