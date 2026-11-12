Application.bind("diagnostics.collect", function (type) {
    Logger.info("Collect diagnostics '" + type + "'");
    DataSet.post("collect", {type: type}, function (json) {
        Application.showInfoAlert("Diagnostics", json.message);
        Utils.defer(DataSet.refresh, 2000);
    }, {dataType: "json"});
});
