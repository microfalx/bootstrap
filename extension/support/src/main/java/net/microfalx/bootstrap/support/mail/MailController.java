package net.microfalx.bootstrap.support.mail;

import net.microfalx.argus.report.ReportService;
import net.microfalx.bootstrap.dataset.DataSetException;
import net.microfalx.bootstrap.dataset.DataSetService;
import net.microfalx.bootstrap.dataset.annotation.DataSet;
import net.microfalx.bootstrap.help.annotation.Help;
import net.microfalx.bootstrap.mail.MailService;
import net.microfalx.bootstrap.model.Field;
import net.microfalx.bootstrap.web.component.Button;
import net.microfalx.bootstrap.web.component.Toolbar;
import net.microfalx.bootstrap.web.dataset.SystemDataSetController;
import net.microfalx.bootstrap.web.util.JsonResponse;
import net.microfalx.lang.ExceptionUtils;
import net.microfalx.lang.StringUtils;
import net.microfalx.resource.MemoryResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.InetAddress;
import java.time.ZonedDateTime;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

@Controller
@RequestMapping(value = "/support/mail")
@DataSet(model = Mail.class, viewTemplate = "support/view_mail", viewClasses = "modal-xl")
@Help("support/mail")
public class MailController extends SystemDataSetController<Mail, String> {

    /**
     * Mails can contain active content, so the browser is instructed to render the body in a sandbox (no scripts,
     * no forms, unique origin), even when the content is opened outside the iframe.
     */
    private static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";
    private static final String CONTENT_SECURITY_POLICY = "sandbox";

    private final MailService mailService;

    public MailController(DataSetService dataSetService, MailService mailService) {
        super(dataSetService);
        this.mailService = mailService;
    }

    /**
     * Returns the body of a mail inline (not as an attachment), to be displayed inside an iframe.
     *
     * @param id the identifier of the mail
     * @return the body
     */
    @GetMapping("{id}/content")
    @ResponseBody()
    public final ResponseEntity<String> content(@PathVariable("id") String id) {
        net.microfalx.bootstrap.mail.Mail mail = mailService.getMail(id)
                .orElseThrow(() -> new DataSetException("Mail with identifier '" + id + "' does not exist"));
        if (!mail.hasBody()) throw new DataSetException("Mail with identifier '" + id + "' has no body");
        MediaType mediaType = new MediaType(mail.isHtml() ? MediaType.TEXT_HTML : MediaType.TEXT_PLAIN, UTF_8);
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header(CONTENT_SECURITY_POLICY_HEADER, CONTENT_SECURITY_POLICY)
                .body(mail.getBody());
    }

    /**
     * Sends a test email to the recipients configured in the (Argus) report settings.
     *
     * @return the outcome
     */
    @GetMapping("test")
    @ResponseBody()
    public final JsonResponse<?> test() {
        String recipients = ReportService.getInstance().getSettings().getRecipients();
        List<String> addresses = List.of(StringUtils.split(recipients, ","));
        if (addresses.isEmpty()) {
            return JsonResponse.fail("No report recipients are configured, a test email cannot be sent");
        }
        String hostName = getHostName();
        String subject = "Test Email - " + hostName;
        String body = "This is a test email sent from '" + hostName + "' at " + ZonedDateTime.now()
                + ".\n\nIf you received this message, the mail configuration works.";
        for (String address : addresses) {
            mailService.send(address, subject, MemoryResource.create(body));
        }
        return JsonResponse.success("A test email was queued for " + String.join(", ", addresses));
    }

    @Override
    protected void updateToolbar(Toolbar toolbar) {
        super.updateToolbar(toolbar);
        toolbar.add(new Button().setAction("mail.test").setText("Test").setIcon("fa-solid fa-paper-plane")
                .setDescription("Sends a test email to the recipients configured for reports"));
    }

    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }

    @Override
    protected void beforeView(net.microfalx.bootstrap.dataset.DataSet<Mail, Field<Mail>, String> dataSet,
                              Model controllerModel, Mail dataSetModel) {
        super.beforeView(dataSet, controllerModel, dataSetModel);
        if (dataSetModel == null) return;
        mailService.getMail(dataSetModel.getId()).ifPresent(mail -> {
            controllerModel.addAttribute("hasBody", mail.hasBody());
            if (mail.getThrowable() != null) {
                controllerModel.addAttribute("stackTrace", ExceptionUtils.getStackTrace(mail.getThrowable()));
            }
        });
    }
}
