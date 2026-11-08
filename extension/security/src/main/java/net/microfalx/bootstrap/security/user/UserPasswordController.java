package net.microfalx.bootstrap.security.user;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.microfalx.bootstrap.security.SecurityContext;
import net.microfalx.bootstrap.web.controller.PageController;
import net.microfalx.bootstrap.web.util.JsonResponse;
import net.microfalx.lang.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import static net.microfalx.lang.StringUtils.isEmpty;

@Controller
@RequestMapping("user/change-password")
public class UserPasswordController extends PageController {

    @Autowired private UserService userService;

    @GetMapping("")
    public String get(Model model) {
        model.addAttribute("user", new UserForm());
        return "security/user::#user-password-change";
    }

    @PostMapping("")
    @ResponseBody
    public JsonResponse<?> save(@ModelAttribute UserForm userForm) {
        User user = SecurityContext.get().getUser();
        if (user.isExternal()) {
            return JsonResponse.fail("External user cannot have the password changed");
        } else {
            if (isEmpty(userForm.getCurrentPassword()) || isEmpty(userForm.getNewPassword())
                    || isEmpty(userForm.getNewPasswordConfirmation())) {
                return JsonResponse.fail("Current password, new password and confirmation are required");
            }
            if (!userService.validate(user.getUsername(), userForm.getCurrentPassword())) {
                return JsonResponse.fail("Current password is invalid");
            }
            if (!ObjectUtils.equals(userForm.getNewPassword(), userForm.getNewPasswordConfirmation())) {
                return JsonResponse.fail("New password and confirmation do not match");
            }
            if (ObjectUtils.equals(userForm.getCurrentPassword(), userForm.getNewPassword())) {
                return JsonResponse.fail("New password must be different from the current password");
            }
            userService.updatePassword(user.getUsername(), userForm.getNewPassword());
            return JsonResponse.success().setPayload(userForm);
        }
    }

    @Getter
    @Setter
    @ToString
    public static class UserForm {

        @Size(min = 1, max = 100)
        private String currentPassword;
        @Size(min = 6, max = 100)
        private String newPassword;
        @Size(min = 6, max = 100)
        private String newPasswordConfirmation;
    }
}
