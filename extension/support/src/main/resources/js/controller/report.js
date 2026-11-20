Application.bind("report.generate", function () {
    Logger.info("Generate report");
    Application.open("/support/report", {}, true);
});

Application.bind("report.send", function (interval) {
    Logger.info("Send report for interval '" + interval + "'");
    DataSet.post("send", {interval: interval}, function (json) {
        Application.showInfoAlert("Report", json.message);
        Utils.defer(DataSet.refresh, 5000);
    }, {dataType: "json"});
});
