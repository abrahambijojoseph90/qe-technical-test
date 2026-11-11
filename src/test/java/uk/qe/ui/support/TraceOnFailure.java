package uk.qe.ui.support;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Tracing;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

/**
 * Keeps the Playwright trace (screenshots, DOM snapshots, network calls) for failed
 * tests only. Passing tests throw theirs away so target/ does not fill up.
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
            String fileName = extensionContext.getRequiredTestClass().getSimpleName()
                    + "-" + extensionContext.getRequiredTestMethod().getName()
                    + "-" + System.currentTimeMillis() + ".zip";
            Path tracePath = Paths.get("target", "traces", fileName);
            context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
            System.out.println("Test failed - trace saved to " + tracePath.toAbsolutePath());
        } else {
            context.tracing().stop();
        }
    }
}
