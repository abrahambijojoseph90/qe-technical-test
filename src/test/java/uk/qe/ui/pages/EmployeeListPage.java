package uk.qe.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * PIM > Employee List
 */
public class EmployeeListPage {

    private final Page page;
    private final Locator employeeIdFilter;
    private final Locator searchButton;
    private final Locator resultRows;
    private final Locator recordCount;

    public EmployeeListPage(Page page) {
        this.page = page;
        this.employeeIdFilter = FormField.inputLabelled(page, "Employee Id");
        this.searchButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Search"));
        this.resultRows = page.locator(".oxd-table-body .oxd-table-card");
        this.recordCount = page.locator(".orangehrm-horizontal-padding > span.oxd-text");
    }

    public EmployeeListPage open() {
        page.navigate("/web/index.php/pim/viewEmployeeList");
        employeeIdFilter.waitFor();
        return this;
    }

    public EmployeeListPage searchByEmployeeId(String id) {
        employeeIdFilter.fill(id);
        searchButton.click();
        return this;
    }

    public Locator resultRows() {
        return resultRows;
    }

    public Locator recordCount() {
        return recordCount;
    }
}
