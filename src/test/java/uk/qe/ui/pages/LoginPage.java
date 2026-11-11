package uk.qe.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.regex.Pattern;

public class LoginPage {

    private final Page page;
    private final Locator username;
    private final Locator password;
    private final Locator loginButton;
    private final Locator errorAlert;

    public LoginPage(Page page) {
        this.page = page;
        this.username = page.locator("input[name='username']");
        this.password = page.locator("input[name='password']");
        this.loginButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Login"));
        this.errorAlert = page.getByRole(AriaRole.ALERT);
    }

    public void open() {
        page.navigate("/web/index.php/auth/login");
    }

    public void logInAs(String user, String pass) {
        username.fill(user);
        password.fill(pass);
        loginButton.click();
    }

    public void waitUntilLoggedIn() {
        page.waitForURL(Pattern.compile(".*/dashboard/index"));
    }

    public Locator errorAlert() {
        return errorAlert;
    }
}
