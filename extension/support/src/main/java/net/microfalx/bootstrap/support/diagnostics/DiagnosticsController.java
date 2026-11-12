package net.microfalx.bootstrap.support.diagnostics;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.diagnostics.Diagnostics.Type;
import net.microfalx.bootstrap.dataset.DataSetException;
import net.microfalx.bootstrap.dataset.DataSetService;
import net.microfalx.bootstrap.dataset.annotation.DataSet;
import net.microfalx.bootstrap.diagnostics.DiagnosticsService;
import net.microfalx.bootstrap.help.annotation.Help;
import net.microfalx.bootstrap.model.Field;
import net.microfalx.bootstrap.web.component.*;
import net.microfalx.bootstrap.web.dataset.DataSetController;
import net.microfalx.bootstrap.web.util.JsonResponse;
import net.microfalx.lang.EnumUtils;
import net.microfalx.resource.Resource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;

import static net.microfalx.lang.StringUtils.isEmpty;

@Slf4j
@Controller
@RequestMapping(value = "/support/diagnostics")
@DataSet(model = Diagnostics.class, viewTemplate = "support/view_diagnostics", viewClasses = "modal-xl",
        canDownload = true)
@Help("support/diagnostics")
public class DiagnosticsController extends DataSetController<Diagnostics, String> {

    private static final int HEAD_LINE_COUNT = 200;
    private static final String COLLECT_ACTION = "diagnostics.collect";
    private static final String TYPE_PARAMETER = "type";

    private final DiagnosticsService diagnosticsService;

    public DiagnosticsController(DataSetService dataSetService, DiagnosticsService diagnosticsService) {
        super(dataSetService);
        this.diagnosticsService = diagnosticsService;
    }

    @PostMapping("collect")
    @ResponseBody()
    public final JsonResponse<?> collect(@RequestParam(TYPE_PARAMETER) String typeName) {
        Type type = EnumUtils.fromName(Type.class, typeName);
        String name = getLabel(type);
        boolean scheduled = diagnosticsService.collect(type, name, "Requested by user");
        if (!scheduled) return JsonResponse.success("A " + name + " is already being collected");
        return JsonResponse.success("Collecting " + name + ", it will be available shortly");
    }

    /**
     * Returns the content of a diagnostics inline (not as an attachment), to be displayed inside an iframe.
     *
     * @param id the identifier of the diagnostics
     * @return the content
     */
    @GetMapping("{id}/content")
    @ResponseBody()
    public final ResponseEntity<InputStreamResource> content(@PathVariable("id") String id) throws IOException {
        net.microfalx.argus.diagnostics.Diagnostics diagnostics = diagnosticsService.getDiagnostics(id)
                .orElseThrow(() -> new DataSetException("Diagnostics with identifier '" + id + "' does not exist"));
        Resource resource = diagnostics.getResource();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(diagnostics.getMimeType().getValue()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new InputStreamResource(resource.getInputStream(true)));
    }

    @Override
    protected void beforeView(net.microfalx.bootstrap.dataset.DataSet<Diagnostics, Field<Diagnostics>, String> dataSet,
                              Model controllerModel, Diagnostics dataSetModel) {
        super.beforeView(dataSet, controllerModel, dataSetModel);
        if (dataSetModel == null) return;
        diagnosticsService.getDiagnostics(dataSetModel.getId()).ifPresent(diagnostics -> {
            if (diagnostics.isHtml()) {
                controllerModel.addAttribute("html", true);
            } else {
                controllerModel.addAttribute("content", getHead(diagnostics));
            }
        });
    }

    @Override
    protected Resource download(net.microfalx.bootstrap.dataset.DataSet<Diagnostics, Field<Diagnostics>, String> dataSet,
                                Model controllerModel, Diagnostics dataSetModel) {
        net.microfalx.argus.diagnostics.Diagnostics diagnostics = diagnosticsService.getDiagnostics(dataSetModel.getId())
                .orElseThrow(() -> new DataSetException("Diagnostics '" + dataSetModel.getName() + "' does not exist"));
        // the data kept in memory has no file name, so it is restored from the original location
        return diagnostics.getResource();
    }

    @Override
    protected void updateToolbar(Toolbar toolbar) {
        super.updateToolbar(toolbar);
        toolbar.add(createCollectButton(Type.THREAD_DUMP, "fa-solid fa-layer-group",
                "Collects a dump of all threads"));
        toolbar.add(createCollectButton(Type.HISTOGRAM, "fa-solid fa-chart-bar",
                "Collects a histogram of the objects in the heap"));
        toolbar.add(new Button().setText("More").setIcon("fa-solid fa-stethoscope")
                .setDescription("Collects other diagnostics").setMenu(createCollectMenu()));
    }

    private Menu createCollectMenu() {
        Menu menu = new Menu().setId("collect");
        menu.add(createCollectItem(Type.HEAP_DUMP, "fa-solid fa-memory",
                "Collects a dump of the heap (can take a while and pause the application)"));
        menu.add(createCollectItem(Type.VM_REPORT, "fa-solid fa-file-lines",
                "Collects a report about the JVM (flags, memory, threads, etc)"));
        menu.add(new Separator());
        menu.add(createCollectItem(Type.PROFILE, "fa-solid fa-microchip",
                "Collects a CPU profile of the application"));
        menu.add(createCollectItem(Type.FLIGHT_RECORDING, "fa-solid fa-plane",
                "Collects a Java Flight Recording"));
        return menu;
    }

    private Button createCollectButton(Type type, String icon, String description) {
        return new Button().setAction(COLLECT_ACTION).setText(getLabel(type)).setIcon(icon)
                .setDescription(description).addParameter(TYPE_PARAMETER, type.name());
    }

    private Item createCollectItem(Type type, String icon, String description) {
        return new Item().setAction(COLLECT_ACTION).setText(getLabel(type)).setIcon(icon)
                .setDescription(description).addParameter(TYPE_PARAMETER, type.name());
    }

    private String getHead(net.microfalx.argus.diagnostics.Diagnostics diagnostics) {
        try {
            return diagnostics.getHead(HEAD_LINE_COUNT);
        } catch (IOException e) {
            LOGGER.atWarn().setCause(e).log("Failed to extract content for diagnostics '{}'", diagnostics.getName());
            return null;
        } catch (Exception e) {
            // the resource might not be available anymore (not collected yet, failed, or removed)
            return null;
        }
    }

    private static String getFileName(net.microfalx.argus.diagnostics.Diagnostics diagnostics) {
        URI uri = diagnostics.getUri();
        String path = uri != null ? uri.getPath() : null;
        if (isEmpty(path)) return diagnostics.getId();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String getLabel(Type type) {
        return switch (type) {
            case THREAD_DUMP -> "Thread Dump";
            case HISTOGRAM -> "Histogram";
            case HEAP_DUMP -> "Heap Dump";
            case VM_REPORT -> "VM Report";
            case PROFILE -> "Profile";
            case FLIGHT_RECORDING -> "Flight Recording";
        };
    }
}
