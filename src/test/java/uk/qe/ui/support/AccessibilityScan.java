package uk.qe.ui.support;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.AxeResults;
import com.deque.html.axecore.results.Rule;
import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Runs the axe-core accessibility rules on the current page and compares the result
 * with the issues we already know about for that page.
 *
 * Only the WCAG A and AA rules are run (2.0, 2.1 and 2.2), which is the level UK public
 * sector services must meet. Every scan also writes a readable report to
 * target/accessibility/<page>.txt, whether it passes or not.
 */
public final class AccessibilityScan {

    private static final List<String> WCAG_AA_TAGS =
            List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa");

    private static final String KNOWN_ISSUES_FILE = "/accessibility/known-issues.properties";

    private static final Path REPORT_FOLDER = Paths.get("target", "accessibility");

    private final String pageName;
    private final List<Rule> violations;

    private AccessibilityScan(String pageName, List<Rule> violations) {
        this.pageName = pageName;
        this.violations = violations;
    }

    public static AccessibilityScan of(Page page, String pageName) {
        AxeResults results = new AxeBuilder(page).withTags(WCAG_AA_TAGS).analyze();
        AccessibilityScan scan = new AccessibilityScan(pageName, results.getViolations());
        scan.writeReport();
        return scan;
    }

    /** Rule ids that failed on this page, e.g. "label" or "color-contrast". */
    public Set<String> failedRules() {
        Set<String> ruleIds = new TreeSet<>();
        for (Rule violation : violations) {
            ruleIds.add(violation.getId());
        }
        return ruleIds;
    }

    /** Rule ids that failed with "serious" or "critical" impact. */
    public Set<String> seriousOrCriticalRules() {
        Set<String> ruleIds = new TreeSet<>();
        for (Rule violation : violations) {
            String impact = violation.getImpact();
            if ("serious".equals(impact) || "critical".equals(impact)) {
                ruleIds.add(violation.getId());
            }
        }
        return ruleIds;
    }

    /** The issues already recorded for this page in known-issues.properties. */
    public Set<String> knownRules() {
        Properties knownIssues = new Properties();
        try (InputStream file = AccessibilityScan.class.getResourceAsStream(KNOWN_ISSUES_FILE)) {
            if (file != null) {
                knownIssues.load(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + KNOWN_ISSUES_FILE, e);
        }

        Set<String> ruleIds = new TreeSet<>();
        String listed = knownIssues.getProperty(pageName, "");
        for (String ruleId : listed.split(",")) {
            if (!ruleId.isBlank()) {
                ruleIds.add(ruleId.trim());
            }
        }
        return ruleIds;
    }

    /** One line per failed rule, for assertion messages. */
    public String summary() {
        StringBuilder text = new StringBuilder();
        for (Rule violation : violations) {
            text.append("\n  ")
                    .append(violation.getId())
                    .append(" [").append(violation.getImpact()).append("] ")
                    .append(violation.getHelp())
                    .append(" (").append(violation.getNodes().size()).append(" elements)");
        }
        text.append("\n  Full report: ").append(REPORT_FOLDER.resolve(pageName + ".txt"));
        return text.toString();
    }

    private void writeReport() {
        List<String> lines = new ArrayList<>();
        lines.add("Accessibility scan: " + pageName);
        lines.add("Rules: WCAG 2.0/2.1/2.2 A and AA (axe-core)");
        lines.add("Violations: " + violations.size());
        lines.add("");

        for (Rule violation : violations) {
            lines.add(violation.getId() + " [" + violation.getImpact() + "]");
            lines.add("  " + violation.getHelp());
            lines.add("  " + violation.getHelpUrl());
            for (int i = 0; i < violation.getNodes().size(); i++) {
                if (i == 5) {
                    lines.add("  ... and " + (violation.getNodes().size() - 5) + " more");
                    break;
                }
                lines.add("  - " + violation.getNodes().get(i).getTarget());
            }
            lines.add("");
        }

        try {
            Files.createDirectories(REPORT_FOLDER);
            Files.write(REPORT_FOLDER.resolve(pageName + ".txt"), lines);
            Allure.addAttachment("Accessibility report - " + pageName, "text/plain", String.join(System.lineSeparator(), lines), "txt");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the accessibility report", e);
        }
    }
}
