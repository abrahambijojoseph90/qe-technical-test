package uk.qe.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * OrangeHRM's form labels are not linked to their inputs (no "for" attribute), so
 * getByLabel() does not work. Every field sits inside a div.oxd-input-group with its
 * label, so we find the group by its label text and then the input inside it.
 */
public final class FormField {

    private FormField() {
    }

    public static Locator inputLabelled(Page page, String labelText) {
        Locator group = page.locator(".oxd-input-group")
                .filter(new Locator.FilterOptions().setHas(
                        page.locator("label", new Page.LocatorOptions().setHasText(labelText))));
        return group.locator("input");
    }

    public static Locator errorUnder(Locator input) {
        // The validation message is a sibling of the input's wrapper inside the same group
        Locator group = input.locator("xpath=ancestor::div[contains(@class,'oxd-input-group')][1]");
        return group.locator(".oxd-input-field-error-message");
    }
}
