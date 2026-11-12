package uk.qe.ui.support;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

/**
 * Keeps the Playwright trace (screenshots, DOM snapshots, network calls) for failed
 * tests only. Passing tests throw theirs away so target/ does not fill up.
 *
 * On a failure it also attaches a screenshot and the trace to the Allure report, so
 * whoever reads the report can see the page as it was when the test failed.
 */
public class TraceOnFailure implements AfterTestExecutionCallback {

    private final Supplier<BrowserContext> contextSupplier;

    public TraceOnFailure(Supplier<BrowserContext> contextSupplier) {
        this.contextSupplier = contextSupplier;
    }

    @Override
    public void afterTestExecution(ExtensionContext extensionContext) {
        BrowserContext context = contextSupplier.get();
        if (context == null) {
            return;
        }

        boolean testFailed = extensionContext.getExecutionException().isPresent();
        if (testFailed) {
            attachScreenshot(context);
            String fileName = extensionContext.getRequiredTestClass().getSimpleName()
                    + "-" + extensionContext.getRequiredTestMethod().getName()
                    + "-" + System.currentTimeMillis() + ".zip";
            Path tracePath = Paths.get("target", "traces", fileName);
            context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
            System.out.println("Test failed - trace saved to " + tracePath.toAbsolutePath());
            attachTrace(tracePath);
        } else {
            context.tracing().stop();
        }
    }

    private static void attachScreenshot(BrowserContext context) {
        if (context.pages().isEmpty()) {
            return;
        }
        Page lastPage = context.pages().get(context.pages().size() - 1);
        try {
            byte[] screenshot = lastPage.screenshot(new Page.ScreenshotOptions().setFullPage(true));
            Allure.addAttachment("Screenshot at failure", "image/png", new ByteArrayInputStream(screenshot), "png");
        } catch (RuntimeException e) {
            System.out.println("Could not take a screenshot: " + e.getMessage());
        }
    }

    private static void attachTrace(Path tracePath) {
        try (InputStream trace = Files.newInputStream(tracePath)) {
            Allure.addAttachment("Playwright trace (open at trace.playwright.dev)", "application/zip", trace, "zip");
        } catch (IOException e) {
            System.out.println("Could not attach the trace: " + e.getMessage());
        }
    }
}
