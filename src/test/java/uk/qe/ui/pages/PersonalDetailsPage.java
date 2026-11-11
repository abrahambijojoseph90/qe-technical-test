package uk.qe.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * PIM > Employee > Personal Details, the page OrangeHRM opens after a new employee is saved.
 */
public class PersonalDetailsPage {

    private final Locator firstName;
    private final Locator lastName;
    private final Locator employeeId;

    public PersonalDetailsPage(Page page) {
        this.firstName = page.locator("input[name='firstName']");
        this.lastName = page.locator("input[name='lastName']");
        this.employeeId = FormField.inputLabelled(page, "Employee Id");
    }

    public Locator firstName() {
        return firstName;
    }

    public Locator lastName() {
        return lastName;
    }

    public Locator employeeId() {
        return employeeId;
    }
}
