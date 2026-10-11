package net.microfalx.bootstrap.web.controller;

import jakarta.annotation.security.PermitAll;
import net.microfalx.bootstrap.web.application.Asset;
import net.microfalx.bootstrap.web.dashboard.DashboardController;
import net.microfalx.bootstrap.web.dashboard.annotation.Dashboard;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static net.microfalx.lang.StringUtils.EMPTY_STRING;

@RequestMapping("/")
@Dashboard("home")
@Controller
public class HomeController extends DashboardController {

    @GetMapping("favicon.ico")
    @PermitAll
    public ResponseEntity<Object> favicon() {
        return AssetUtils.getResource("favicon.ico", Asset.Type.IMAGE, "favicon_default.ico");
    }

    @Override
    protected String getTitle() {
        return EMPTY_STRING;
    }
}
