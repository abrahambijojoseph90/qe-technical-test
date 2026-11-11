# Petstore API and OrangeHRM UI tests

Playwright for Java, JUnit 5 and Maven.

- API tests run against the Swagger Petstore: https://petstore.swagger.io/v2
- UI tests run against the OrangeHRM demo: https://opensource-demo.orangehrmlive.com

How I chose the scenarios, the API vs UI reasoning, the defects I found and what I would automate next are all in [TEST_DESIGN.md](TEST_DESIGN.md).

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

Only the API tests, only the UI tests, or only the offline self-checks of the test code:

```
mvn test -Dgroups=api
mvn test -Dgroups=ui
mvn test -Dgroups=unit
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

## Configuration

Nothing has to be set to run against the public demos. Each value can be overridden with a `-D` system property or an environment variable:

| Setting | -D property | Environment variable | Default |
|---|---|---|---|
| Petstore base URL | `petstore.baseUrl` | `PETSTORE_BASE_URL` | `https://petstore.swagger.io/v2/` |
| OrangeHRM base URL | `orangehrm.baseUrl` | `ORANGEHRM_BASE_URL` | `https://opensource-demo.orangehrmlive.com` |
| OrangeHRM user | `orangehrm.username` | `ORANGEHRM_USERNAME` | `Admin` |
| OrangeHRM password | `orangehrm.password` | `ORANGEHRM_PASSWORD` | `admin123` |
| Headless | `headless` | `HEADLESS` | `true` |
| Browser | `browser` | `BROWSER` | `chromium` |

The OrangeHRM defaults are the public demo login printed on the demo's own login page. For a real system these would come from environment variables or CI secrets, not from code.

## Reports

- Console: a pass/fail summary per test class, with the full assertion message and response details for anything that fails.
- HTML: after a run, `mvn surefire-report:report-only` writes `target/reports/surefire.html`.
- JUnit XML: `target/surefire-reports/`, which any CI server can read.
- UI failures: a Playwright trace is saved to `target/traces/`. Drop the zip onto https://trace.playwright.dev to step through the failure with screenshots, DOM snapshots and network calls. Traces from passing tests are thrown away.

## Project layout

```
src/test/java/uk/qe
├── config
│   └── TestConfig.java            settings: -D property, then env variable, then default
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
│       ├── PetstoreContractTest.java  live Swagger file vs our saved copy
│       └── ResponseChecksSelfTest.java proves the checks catch bad responses (offline)
└── ui                             OrangeHRM UI tests
    ├── pages                      page objects: Login, Add Employee, Personal Details, Employee List
    ├── support
    │   ├── UiTestBase.java        browser lifecycle, logs in once, tracing
    │   ├── TraceOnFailure.java    keeps the trace only for failed tests
    │   └── OrangeHrmApi.java      clean-up through OrangeHRM's own REST API
    └── tests/AddEmployeeTest.java

src/test/resources/contract/petstore-swagger.json   saved copy of the Petstore contract
```

## Notes on the demo sites

Both sites are shared public demos. Anyone can change or reset the data at any time, and they slow down now and then. The tests create their own uniquely named data and delete it afterwards. If a test still fails, the trace (UI) or the response body in the assertion message (API) usually shows whether the site or the test is at fault.
