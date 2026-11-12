# Petstore API and OrangeHRM UI tests

Playwright for Java, JUnit 5 and Maven.

- API tests run against the Swagger Petstore: https://petstore.swagger.io/v2
- UI tests run against the OrangeHRM demo: https://opensource-demo.orangehrmlive.com

How I chose the scenarios, the API vs UI reasoning, the defects I found and what I would automate next are all in [TEST_DESIGN.md](TEST_DESIGN.md).

## What is tested

| Kind of test | Test class | What it checks |
|---|---|---|
| API | `GetPetByIdTest` | `GET /pet/{petId}` in depth: a created pet comes back exactly, not found, after delete, ids 0 and -1, invalid ids (known defect) |
| Contract | `PetstoreContractTest`, plus a schema check on every response | Responses match the Petstore's Swagger definitions, and the live Swagger file hasn't changed |
| Integration (API) | `PetLifecycleTest` | One pet through create, read, update, find by status and delete, with every endpoint agreeing |
| Integration (API to UI) | `EmployeeSearchTest` | An employee created through OrangeHRM's API is found by the UI search |
| End to end (UI) | `AddEmployeeTest` | An admin adds an employee, sees it saved, and finds it in the employee list; required-field validation |
| Accessibility | `AccessibilityTest` | WCAG 2.2 AA (axe-core) on the login, Add Employee and Employee List pages |
| Security | `OrangeHrmSecurityTest` | No access without a session, session cookie flags, security headers, logout ends the session |
| Unit (test code) | `ResponseChecksSelfTest` | The response checks really reject bad responses. Runs offline. |

The normal run has 38 tests and takes about two minutes. Pull requests get a smaller smoke gate, and the full set runs on GitHub Actions after every merge (see [Continuous integration](#continuous-integration)).

## What you need

- Java 21 (any JDK 21 build)
- Maven 3.9 or newer
- Internet access to the two demo sites

You do not need to install browsers yourself. The first time the UI tests run, Playwright downloads Chromium (around 150 MB) into its own cache. Later runs reuse it.

## Running the tests

Everything:

```
mvn test
```

### Choosing what to run

Every test has tags, and `-Dgroups` picks them (`|` means or, `&` means and):

| Tag | What it covers |
|---|---|
| `smoke` | The few tests that prove the critical paths work. This is the pull request gate. |
| `regression` | Everything except the offline self-checks. |
| `api`, `ui` | Which layer the test drives |
| `unit` | Offline self-checks of the test code itself, no network |
| `integration` | Several parts working together on the same data: a pet through five endpoints, an employee created by API and found in the UI |
| `e2e` | A full user journey through the browser: adding an employee |
| `accessibility` | axe-core WCAG 2.2 AA scans |
| `security` | Access control, session cookie and security header checks |
| `known-defect` | Tests that fail because of a known bug in the demo. Left out by default. |

```
mvn test -Dgroups="smoke | unit"        # the pull request gate, about 30 seconds
mvn test -Dgroups=api
mvn test -Dgroups=ui
mvn test -Dgroups=accessibility
mvn test -Dgroups=security
```

Watch the browser instead of running headless:

```
mvn test -Dgroups=ui -Dheadless=false
```

Another browser (chromium is the default):

```
mvn test -Dgroups=ui -Dbrowser=firefox
```

### Known defect tests

Some tests describe what the Petstore spec says should happen but the demo does not do. They are tagged `known-defect` and left out of the normal run, so a red build always means something new has broken. To see them fail:

```
mvn test -Dgroups=known-defect -DexcludedGroups=
```

## Environments and configuration

Settings live in one file per environment under `src/test/resources/environments/`. The default is `demo.properties`, which points at the public demo sites. Pick another with `-Denv=<name>` or the `TEST_ENV` variable. To add an environment, copy `demo.properties` to `<name>.properties`.

Any single value can be overridden. A `-D` property wins over an environment variable, which wins over the file:

| Setting | -D property | Environment variable |
|---|---|---|
| Petstore base URL | `petstore.baseUrl` | `PETSTORE_BASE_URL` |
| OrangeHRM base URL | `orangehrm.baseUrl` | `ORANGEHRM_BASE_URL` |
| OrangeHRM user | `orangehrm.username` | `ORANGEHRM_USERNAME` |
| OrangeHRM password | `orangehrm.password` | `ORANGEHRM_PASSWORD` |
| Headless | `headless` | `HEADLESS` |
| Browser | `browser` | `BROWSER` |

There are no URLs or passwords in the Java code. The only login in the repo is the OrangeHRM demo login in `demo.properties`, which is public (it is printed on the demo's own login page). Any other environment should leave the login out of its file and supply it from a secret store or CI secrets. If a required setting is missing, the run stops and says which one and how to set it.

## Continuous integration

`.github/workflows/tests.yml` runs on GitHub Actions:

| When | What runs | Why |
|---|---|---|
| Every pull request | Smoke gate: `smoke` and `unit` tests, API and UI, one job | Fast feedback (a few minutes) on the paths that must never break |
| Merge to `main`, weekday mornings, or on demand | API regression and UI regression, as two parallel jobs | Full coverage. The morning run also spots changes on the demo sites. |

To make the smoke gate block merging, add a branch rule on `main` that requires the "Smoke gate" check to pass (Settings > Branches). GitHub only allows this on private repositories with a paid plan; on a free plan the check still runs and shows red or green on the pull request.

Running API and UI tests together is safe, in one job or in parallel jobs. Every test creates its own uniquely named data and deletes it afterwards, each UI test gets its own browser context, and parallel jobs run on separate machines with separate reports.

Reports, and Playwright traces for any failed UI test, are attached to each run as artifacts. Manual runs can choose the environment and the browser. Dependabot opens weekly pull requests for outdated dependencies, and the smoke gate checks each one.

## Reports

**The readable report is Allure.** After a run:

```
mvn allure:report
```

This writes `target/allure-report/index.html`, a single file you can open straight in a browser. It shows every test grouped by suite, with pass or fail, the failure message, how long each took, and the evidence:

- API tests: every response that was checked is attached, so you can see exactly what came back.
- The pet journey test: each step (create, read, update, find, delete) is shown separately.
- Failed UI tests: a full-page screenshot and the Playwright trace. Drop the trace onto https://trace.playwright.dev to step through it with DOM snapshots and network calls.
- Accessibility tests: the list of WCAG rules that failed on each page and the elements involved.

**A saved example is in [`sample-report/index.html`](sample-report/index.html)**, so you can see the report without running anything. It is a real run from 12 Nov 2026 with the known-defect tests switched on: 44 tests, 38 passed and 6 failed. All six failures are the known defects described in [TEST_DESIGN.md](TEST_DESIGN.md#defects-found):

| Failing test | Defect |
|---|---|
| API - GET /pet/{petId}: invalid ids `abc`, `1.5`, `99999999999999999999` (3 tests) | #1: 404 and a leaked Java exception instead of 400 |
| Security: pages cannot be put inside a frame | #7: no clickjacking protection |
| Security: Server header does not reveal versions | #8: `nginx/1.18.0 (Ubuntu)` |
| Accessibility: Add Employee has no serious or critical issues | #10: WCAG failures on the page |

A normal `mvn test` run leaves the known-defect tests out and is fully green.

Other outputs:

- Console: a pass/fail summary per test class, with the full assertion message for anything that fails.
- JUnit XML in `target/surefire-reports/`, which any CI server can read.
- Accessibility reports as text in `target/accessibility/`.

In CI, every run attaches the Allure report, the JUnit XML and the accessibility reports to the run, and the traces of any failed UI test.

## Project layout

```
src/test/java/uk/qe
├── config
│   └── TestConfig.java            settings: -D property, then env variable, then the environment file
├── api                            Petstore API tests
│   ├── client/PetApiClient.java   wraps the /pet calls, no assertions in here
│   ├── data/PetTestData.java      unique test pets
│   ├── model/Pet.java, ApiError.java
│   ├── support
│   │   ├── PetstoreContract.java    checks JSON against the Swagger definitions
│   │   ├── PetstoreAssertions.java  shared checks, e.g. the full error check
│   │   └── StrictJson.java          JSON reader that fails on wrong types or extra fields
│   └── tests
│       ├── GetPetByIdTest.java        the endpoint tests
│       ├── PetLifecycleTest.java      one pet across five endpoints
│       ├── PetstoreContractTest.java  live Swagger file vs our saved copy
│       └── ResponseChecksSelfTest.java proves the checks catch bad responses (offline)
└── ui                             OrangeHRM UI tests
    ├── pages                      page objects: Login, Add Employee, Personal Details, Employee List
    ├── support
    │   ├── UiTestBase.java        browser lifecycle, logs in once, tracing
    │   ├── TraceOnFailure.java    keeps the trace only for failed tests
    │   ├── AccessibilityScan.java runs axe-core and writes the report
    │   └── OrangeHrmApi.java      clean-up through OrangeHRM's own REST API
    └── tests
        ├── AddEmployeeTest.java
        ├── EmployeeSearchTest.java
        ├── AccessibilityTest.java
        └── OrangeHrmSecurityTest.java

src/test/resources
├── environments/demo.properties          settings for the public demos
├── contract/petstore-swagger.json        saved copy of the Petstore contract
└── accessibility/known-issues.properties accessibility issues already found, by page

.github
├── workflows/tests.yml                   smoke gate on pull requests, regression on main
└── dependabot.yml
```

## Notes on the demo sites

Both sites are shared public demos. Anyone can change or reset the data at any time, and they slow down now and then. The tests create their own uniquely named data and delete it afterwards. If a test still fails, the trace (UI) or the response body in the assertion message (API) usually shows whether the site or the test is at fault.
