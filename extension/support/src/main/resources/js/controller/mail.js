Application.bind("mail.test", function () {
    DataSet.get("test", {}, function (json) {
        Application.showInfoAlert("Mail", json.message);
        Utils.defer(DataSet.refresh, 2000);
    }, {dataType: "json"});
});
