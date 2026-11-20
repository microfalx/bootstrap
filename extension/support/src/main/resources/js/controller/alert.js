Application.bind("alert.acknowledge", function () {
    DataSet.get("acknowledge", {}, function (json) {
        Application.showInfoAlert("Alerts", json.message);
        Utils.defer(DataSet.refresh, 2000);
    }, {dataType: "json"});
});

Application.bind("alert.clear", function () {
    Application.question("Alert", "Are you sure you want to clear all alerts?", function () {
        DataSet.get("clear", {}, function (json) {
            Application.showInfoAlert("Alerts", json.message);
            Utils.defer(DataSet.refresh, 2000);
        }, {dataType: "json"});
    });
});