package net.microfalx.bootstrap.support.mail;

import net.microfalx.bootstrap.dataset.DataSetException;
import net.microfalx.bootstrap.dataset.DataSetService;
import net.microfalx.bootstrap.dataset.annotation.DataSet;
import net.microfalx.bootstrap.help.annotation.Help;
import net.microfalx.bootstrap.mail.MailService;
import net.microfalx.bootstrap.model.Field;
import net.microfalx.bootstrap.web.dataset.SystemDataSetController;
import net.microfalx.lang.ExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

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
