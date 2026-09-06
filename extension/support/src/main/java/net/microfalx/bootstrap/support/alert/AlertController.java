package net.microfalx.bootstrap.support.alert;

import net.microfalx.argus.api.LoggerEvent;
import net.microfalx.bootstrap.dataset.DataSetService;
import net.microfalx.bootstrap.dataset.annotation.DataSet;
import net.microfalx.bootstrap.help.annotation.Help;
import net.microfalx.bootstrap.logger.LoggerService;
import net.microfalx.bootstrap.model.Field;
import net.microfalx.bootstrap.web.component.Button;
import net.microfalx.bootstrap.web.component.Toolbar;
import net.microfalx.bootstrap.web.dataset.DataSetController;
import net.microfalx.bootstrap.web.util.JsonResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(value = "/support/alert")
@DataSet(model = Alert.class, viewTemplate = "support/view_alert", viewClasses = "modal-xl", defaultQuery = "acknowledged = false")
@Help("support/alert")
public class AlertController extends DataSetController<Alert, String> {

    private final LoggerService loggerService;

    public AlertController(DataSetService dataSetService, LoggerService loggerService) {
        super(dataSetService);
        this.loggerService = loggerService;
    }

    @GetMapping("acknowledge")
    @ResponseBody()
    public final JsonResponse<?> acknowledge() {
        long count = loggerService.acknowledgeAlerts();
        return JsonResponse.success(count + " alerts have been acknowledged");
    }

    @GetMapping("clear")
    @ResponseBody()
    public final JsonResponse<?> clear() {
        long count = loggerService.clearAlerts();
        return JsonResponse.success(count + " alerts have been removed");
    }

    @Override
    protected void beforeView(net.microfalx.bootstrap.dataset.DataSet<Alert, Field<Alert>, String> dataSet, Model controllerModel, Alert dataSetModel) {
        super.beforeView(dataSet, controllerModel, dataSetModel);
        if (dataSetModel != null) {
            net.microfalx.argus.api.Alert alert = loggerService.getAlert(dataSetModel.getId());
            controllerModel.addAttribute("alertClass", dataSetModel.getLevel() == LoggerEvent.Level.ERROR ? "alert-danger" : "alert-warning");
            if (alert != null) {
                controllerModel.addAttribute("alert", alert);
                controllerModel.addAttribute("message", org.apache.commons.lang3.StringUtils.abbreviate(alert.getMessage(), 100));
                controllerModel.addAttribute("stackTrace", alert.getEvent().getExceptionStackTrace());
            }
        }
    }

    @Override
    protected void updateToolbar(Toolbar toolbar) {
        super.updateToolbar(toolbar);
        toolbar.add(new Button().setAction("alert.acknowledge").setText("Acknowledge").setIcon("fa-solid fa-thumbs-up")
                .setDescription("Acknowledges all pending alerts"));
        toolbar.add(new Button().setAction("alert.clear").setText("Clear").setIcon("fa-solid fa-broom")
                .setDescription("Removes all alerts"));
    }
}
