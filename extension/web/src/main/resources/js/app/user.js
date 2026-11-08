/*
* The Content Variables
 */
window.User = window.User || {};

/**
 * Returns the username of the current user.
 *
 * @return {boolean} true if the user is authenticated, false otherwise
 */
User.isAuthenticated = function () {
    return Utils.defaultIfNotDefinedOrNull(this.getData().authenticated, false);
}

/**
 * Returns the username of the current user.
 *
 * @return {string} the username
 */
User.getId = function () {
    return Utils.defaultIfNotDefinedOrNull(this.getData().id, "anonymous");
}

/**
 * Returns the username of the current user.
 *
 * @return {string} the username
 */
User.getUserName = function () {
    return Utils.defaultIfNotDefinedOrNull(this.getData().userName, "anonymous");
}

/**
 * Returns the (display) name of the current user.
 *
 * @return {string} the name
 */
User.getName = function () {
    return Utils.defaultIfNotDefinedOrNull(this.getData().name, "Anonymous");
}

/**
 * Returns the email of the current user.
 *
 * @return {string} the email, null if not available
 */
User.getEmail = function () {
    return this.getData().email;
}

/**
 * Returns the email of the current user.
 *
 * @return {boolean} true if the user has the role, false otherwise
 */
User.hasRole = function (role) {
    Utils.requireNonNull(role);
    if (!this.roles) {
        this.roles = {};
        let existingRoles = this.getData().roles || {};
        for (const existingRole of existingRoles) {
            this.roles[existingRole.toUpperCase()] = 1;
        }
    }
    return this.roles[role.toUpperCase()] === 1;
}

/**
 * Returns an object with user data (info).
 *
 * @return {Object} the data associated with the user
 */
User.getData = function () {
    return APP_USER || {};
}

/**
 * Shows the user profile.
 */
User.showProfile = function () {
    Application.get("/user/profile", {}, function (data) {
        Application.loadModal("user-profile", data);
    }, {self: false});
}

/**
 * Saves the profile form.
 */
User.saveProfile = function () {
    const me = this;
    Application.saveForm('#user-profile-form', "/user/profile", {}, {
        self: false,
        success: function (data) {
            Application.showInfoAlert("Profile", "The profile information has been saved");
            me.updateInfo(data.payload);
            Application.closeModal();
        }
    });
}

/**
 * Shows the user settings.
 */
User.showSettings = function () {
    Application.get("/user/settings", {}, function (data) {
        Application.loadModal("user-settings", data);
    }, {self: false});
}

/**
 * Saves the settings form.
 */
User.saveSettings = function () {
    Application.saveForm('#user-settings-form', "/user/settings", {}, {
        self: false,
        success: function (data) {
            Application.showInfoAlert("Settings", "The settings information has been saved");
            Application.closeModal();
        }
    });
}

/**
 * Shows the user change password form.
 */
User.showChangePassword = function () {
    Application.get("/user/change-password", {}, function (data) {
        Application.loadModal("user-password-change", data);
    }, {self: false});
}

/**
 * Saves the change password form.
 */
User.changePassword = function () {
    const me = this;
    Application.saveForm('#user-password-change-form', "/user/change-password", {}, {
        self: false,
        success: function (data) {
            Application.showInfoAlert("Password", "The password has been changed successfully");
            Application.closeModal();
        }
    });
}

/**
 * Initializes the user
 */
User.initialize = function () {
    Logger.debug("Initialize user: " + this.getName() + " (" + this.getUserName() + ")");
}

/**
 * Starts user related functionality
 */
User.start = function () {
    if (this.getData().resetPassword) {
        const key = "user.password-reset-reminder." + this.getId();
        const lastShown = LocalStorage.get(key);
        const now = Date.now();
        if (typeof lastShown !== "number" || !Number.isFinite(lastShown) || now - lastShown >= 4 * 60 * 60 * 1000) {
            Application.showWarnAlert("Security", "Your password was reset recently by the system administrator or yourself. Please change your password to a password known only to you, as soon as possible. <br><br>Change password <a href='#' onclick='User.showChangePassword()'>now</a>?");
            LocalStorage.set(key, now);
        }
    }
}

/**
 * Updates user info.
 *
 * @param {Object} user the user object containing the updated user information
 */
User.updateInfo = function (user) {
    this.getData().name = user.name;
    this.getData().email = user.email;
    $('.user-display-name').html(user.name);
    $('.user-email').html(user.email);
}

// initialize user
User.initialize();