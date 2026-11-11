# Test Design Explanation

## How I approached it

The brief asks for one API test and one UI test in about an hour. So I kept the code small and spent the effort on choosing *what* to test and *where* to test it. Everything else I would do with more time is in the priority lists at the end.

The same tool, Playwright, runs both layers. Playwright's `APIRequestContext` drives the Petstore, and its browser API drives OrangeHRM. One dependency, one way of waiting and asserting, and the UI tests can use the API for set-up and clean-up with the same logged-in session.

The structure:

- **API:** a thin client (`PetApiClient`) that only sends requests and hands back responses. The test class owns every assertion. Keeping assertions out of the client means a negative test can send something deliberately wrong without the client getting in the way.
- **UI:** page objects for the four screens involved. Pages expose actions (`enterName`, `saveNewEmployee`) and locators. Assertions stay in the test, so a reader sees what is being checked without opening five files.
- **Test data:** every test creates its own data with a random id and deletes it afterwards. Both sites are shared with the whole internet, so fixed ids like `1` get overwritten by other people.
- **Config:** one class. A `-D` property wins over an environment variable, which wins over a default. Defaults point at the public demos so `mvn test` works on a clean machine.

## API: GET /pet/{petId}

I picked `GET /pet/{petId}` because almost every other Petstore journey depends on it. If you cannot read a pet back reliably, create, update and delete cannot be verified either. It also has a clear contract in the Swagger file: 200 with the pet, 400 for an invalid id, 404 for a missing pet.

POST and DELETE are only used to set up and clean up. Every check is on what GET returns.

### How responses are checked

Checking only the status code would let a lot through, so every response body is checked three ways:

1. **Against the contract.** The body is validated against the `Pet` or `ApiResponse` definition from the Petstore's own Swagger file, using a JSON Schema validator. A saved copy of the file lives in `src/test/resources/contract`. I made the definitions strict (no undocumented fields), because Swagger allows extra fields by default and a renamed field would otherwise slip through as "an extra field plus a missing optional one".
2. **Strict typing.** The body is read into a Java record with a JSON reader that refuses to convert anything. Jackson normally accepts `"123"` (text) for a number and quietly ignores unknown fields. Here both fail.
3. **The values.** For a pet, the whole record is compared with what was created, so one wrong field fails the test and the message shows which. For errors, one shared check (`assertPetstoreError`) is used by every negative test: status code, JSON content type, and `code`, `type` and `message` all exactly as expected. For example, `{"code":1,"type":"error","message":"Pet not found"}`.

**How do we know the contract hasn't changed?** `PetstoreContractTest` downloads the live `swagger.json` and compares the definitions and documented responses we rely on with our saved copy. If the API team changes them, that test fails with a clear message, rather than the endpoint tests failing in confusing ways. We then decide on purpose whether to update the copy.

**Testing the tests.** A check that can never fail gives false confidence. `ResponseChecksSelfTest` feeds hand-written bad responses into the schema check and the strict reader: id as text, required field missing, status not in the allowed list, an extra field, a renamed field, a wrong type nested inside `category`. It confirms every one is rejected. It needs no network and runs in a fraction of a second.

| # | Scenario | Type | Why |
|---|---|---|---|
| 1 | A pet that was just created comes back with status 200, matches the Pet contract, and has exactly the same fields | Positive | The core promise of the endpoint. Comparing whole records catches a single wrong field (status, tags, category) that a "name equals X" check would miss. |
| 2 | An id that was never created returns the full "Pet not found" error | Negative | The most common client error. The whole error is checked, not only the status, because clients rely on the code and show the message to users. |
| 3 | A pet that has been deleted returns the full "Pet not found" error | Negative | Data lifecycle. A cache or soft delete that still serves deleted records is a real bug class, and a data protection problem for personal data. |
| 4 | Ids 0 and -1 return the full "Pet not found" error | Negative, boundary | The edges of the id range. They should be handled cleanly, not crash. |
| 5 | Ids that are not whole numbers (`abc`, `1.5`, `99999999999999999999`) return 400 | Negative, **known defect** | The spec says 400 "Invalid ID supplied". The demo returns 404 and leaks a Java exception. See defects below. |

Test 5 is tagged `known-defect` and left out of the default run. I would rather keep a test that states the correct behaviour and fails than bend it to pass against a bug. It stays visible and runnable (`mvn test -Dgroups=known-defect -DexcludedGroups=`), but it does not turn every build red for a bug that is already known and recorded. In a real team it would link to the bug ticket and come back into the main run once the fix ships.

## UI: adding an employee in OrangeHRM

I picked **PIM > Add Employee** over the login page. Adding people is the main job of an HR system, and this one journey covers a form, validation, a save that calls the back end, a redirect and a search on a second page. Login gets exercised anyway, once, at the start of the run.

| # | Scenario | Type | What it shows |
|---|---|---|---|
| 1 | Admin adds an employee with a unique name and employee id. The success message shows, the Personal Details page has the right values, and searching the Employee List by that id finds exactly one matching row. | Positive | That the record was saved properly, not only that the screen said "Saved". Searching from a different page proves it reached the database. |
| 2 | Clicking Save with an empty form shows "Required" under first and last name, and the page does not move on | Negative | Validation stops bad data at the door. |

How the page interactions are handled:

- **Locators:** I used roles and visible text where OrangeHRM supports them (`getByRole(BUTTON, "Save")`, the page heading). Where it doesn't, I used stable `name` attributes (`firstName`, `lastName`). OrangeHRM's labels are not linked to their inputs, so `getByLabel` does not work. `FormField.inputLabelled()` finds the input inside the group that holds the label text, rather than relying on position or long CSS chains.
- **Waiting:** there is no `Thread.sleep` anywhere. Playwright waits for elements to be ready, and the web-first assertions (`assertThat(locator).hasValue(...)`) retry until they pass or time out.
- **The save:** `saveNewEmployee()` clicks Save and waits for the `POST /api/v2/pim/employees` response the page sends. It reads the new employee number from it straight away, so clean-up still happens if a later step fails. The first version waited for the redirect and then looked for the success message. It failed because the message disappears during the redirect, which was a useful reminder to assert on short-lived messages first.
- **Login once:** `UiTestBase` logs in once per run and saves the session to `target/auth/admin.json`. Each test gets a fresh browser context loaded with that session. Tests stay isolated (no shared cookies or pages) without paying for a UI login each time.
- **Clean-up through the API:** employees are deleted with OrangeHRM's own REST API, using the browser's session. Clicking through a delete dialog would be slower, flakier, and not what this test is about.
- **Evidence on failure:** every UI test is traced, and the trace is only kept if the test fails. It has screenshots, the DOM at every step and the network calls, which is usually enough to fix a failure without re-running it.

## API layer or UI layer?

My rule of thumb: **test a rule at the lowest layer that can prove it, and keep the UI for what only the UI can show.**

UI tests are slow (seconds, not milliseconds), depend on rendering and timing, and fail for reasons unrelated to the rule under test. API tests give the same answer faster, more reliably, and closer to the code that broke.

Examples from this exercise:

| Rule | Where I'd test it | Why |
|---|---|---|
| A saved employee can be read back with the same data | API, many data variations | Pure data persistence. The UI test checks it once end to end. Combinations of names, ids, special characters and lengths belong at the API. |
| First and last name are required | API (and unit) for every rule, plus one UI check | The back end must reject bad data even if someone bypasses the form. The UI test only proves the message is shown to the user. |
| Employee id must be unique | API | A duplicate check is server logic. Setting it up through the UI needs two full journeys to test one rule. |
| Search filters (name, id, status, job title) | API | Many combinations. Through the UI each one costs a page load. |
| The admin can find the employee they just added | UI, one journey | This is the user's real experience across two screens. Only an end-to-end test proves the pieces are wired together. |
| Login | API or a stored session for set-up; one UI test for the login page itself | Logging in through the UI before every test makes every test slower and dependent on the login page. |
| Pet status values and invalid ids | API only | There is no Petstore UI, and these are contract rules. |

Some things belong lower still. Validation rules and id parsing (the Petstore defect below) should be unit tests in the service itself, with **contract tests** (for example Pact) checking that the API and its consumers agree on the shape of requests and responses. That leaves end-to-end UI tests for a small number of critical journeys.

The UI test in this repo already mixes layers on purpose: the browser drives the journey being tested, and the API handles everything around it.

## Defects found in the Petstore demo

I found these while exploring the API. The spec I compared against is https://petstore.swagger.io/v2/swagger.json.

| # | Request | Expected (spec) | Actual | Impact |
|---|---|---|---|---|
| 1 | `GET /pet/abc` (also `1.5`, `99999999999999999999`) | 400 Invalid ID supplied | 404, message `java.lang.NumberFormatException: For input string: "abc"` | Wrong status code, and the response leaks implementation detail (Java, how ids are parsed). That is an information disclosure finding in a security review. Automated in the `known-defect` test. |
| 2 | `POST /pet` without `name` or `photoUrls` | Rejected; both are `required` in the definition | 200, the pet is created | Invalid records get into the store, and every reader has to cope with missing fields. |
| 3 | `POST /pet` with `"id": "notanumber"` | 405 Invalid input | 500 "something bad happened" | A client mistake is reported as a server failure. It would page the wrong team and hide real outages in the error rate. |
| 4 | `POST /pet` with `{}` | Rejected | 200, given id `9223372036854775807` (the largest Java long) | Every empty create lands on the same id and overwrites the last one. |
| 5 | `GET /pet/findByStatus?status=flying` | 400 Invalid status value | 200 with an empty list | A typo in a client looks like "no pets", and nobody notices. |
| 6 | `DELETE /pet/{id}` for a pet already deleted | 404 Pet not found | 404 with an empty body | Minor: inconsistent with the JSON error body used everywhere else. |

## Trade-offs and challenges

- **Shared public environments.** Both sites can be changed or reset by anyone, and they are sometimes slow. I handled it with unique data, cleaning up after every test, slightly longer navigation timeouts, and no dependence on pre-existing records. What I can't control is the demo going down. On a real project the tests would run against an environment the team owns, seeded for each run.
- **One endpoint, done properly, vs. many endpoints shallowly.** I chose depth: one endpoint with positive, negative and boundary cases, rather than a happy path on five endpoints.
- **Known defects.** Making the defect tests pass against current behaviour would have hidden the bugs. Leaving them in the main run would make the build permanently red, and people stop trusting a build that is always red. Tagging them was the compromise.
- **How strict to be.** I made the schema stricter than the Swagger file (no extra fields). That catches renames and accidental data leaks, but it also means a harmless new field fails the tests until we accept it. For a contract we depend on, I would rather be told and decide. If the API team added fields often, I would relax it and rely on the contract comparison test instead.
- **Reporting.** Playwright for Java has no built-in HTML reporter like the Node version does. I used the standard Surefire HTML and XML reports, which every CI tool understands, and Playwright traces for the detail. With more time I would add Allure for a richer report with trends.
- **Short-lived success message.** As described above, the first version of the UI test was flaky by design. Waiting for the network response instead of the redirect fixed it properly, and in a way that also made clean-up safer.

## What I would automate next, in priority order

The order is risk first: what would hurt users or the service most if it broke, and what has the best value for its cost.

### Petstore API

1. **Create pet: required fields and types** (`POST /pet` without name or photoUrls, wrong types, malformed JSON). Covers defects 2 to 4. Bad data written to the store is the most expensive kind of bug to clean up later.
2. **Apply the same contract checks to every other endpoint.** The schema check and the full error check are already reusable, so each new endpoint test gets them for free.
3. **Update a pet** (`PUT /pet`, `POST /pet/{id}` form): status changes, updating a pet that does not exist, partial updates.
4. **Find by status:** each valid status returns only pets in that status; an invalid value returns 400 (defect 5); several statuses at once.
5. **Delete:** delete a pet that does not exist, an invalid id, and check the `api_key` header behaviour.
6. **Store and orders** (`/store/order`): place, read and delete an order, quantity and date validation, ordering a pet that is already sold. This is the "money" part of the store.
7. **Users and login** (`/user`, `/user/login`): create, duplicate username, wrong password, and checks that error responses do not reveal whether a username exists.
8. **Image upload:** wrong file types, files that are too large, missing file.
9. **Non-functional:** response time baselines for the read endpoints (k6 or Gatling in a separate pipeline, not in this suite), and basic security checks: no stack traces in errors (defect 1), injection strings in text fields, headers.

### OrangeHRM UI

1. **Login:** wrong password shows "Invalid credentials", empty fields show "Required", logout, and a session that has expired sends you back to login. Every user goes through it, and it is the main security boundary.
2. **Add employee, more cases:** duplicate employee id shows an error, maximum field lengths, special characters and non-English names, adding a photo, and the "Create Login Details" option.
3. **Search the employee list** (mostly via the API, plus one UI check): by name with the autocomplete, by job title and status, no results, and paging.
4. **Edit and delete an employee:** change personal details and confirm they persist, delete with the confirmation dialog, cancel a delete.
5. **Role-based access:** an ESS (non-admin) user cannot see Admin or other people's PIM records, and cannot open them by typing the URL. Admin screens are only useful if non-admins really can't get to them.
6. **Admin > User Management:** create a system user linked to an employee, password rules, disable a user and check they can no longer log in.
7. **Accessibility:** run axe-core on the main pages (login, add employee, employee list), then check keyboard-only use of the add employee form. Public sector services have to meet WCAG 2.2 AA. Automated checks catch roughly a third of issues, so this goes with manual testing, not instead of it.
8. **Cross-browser and mobile viewport:** run the critical journeys on Firefox and WebKit (already supported with `-Dbrowser=`) and a narrow viewport.
9. **Leave and Time** modules: apply for leave, approve and reject, leave balance checks. Important to users, but they need a lot of data set-up, so later.

### The framework itself

1. A CI pipeline (GitHub Actions) running the API tests on every pull request and the UI tests on merge and nightly, publishing the reports and traces as build artefacts.
2. Run tests in parallel once there are more of them. Contexts are already isolated, so the change is small.
3. Seeded test environments the team controls, instead of public demos.
4. Contract tests (Pact) between the front end and the APIs it calls, so most of the "does the UI still match the API" risk is caught without a browser.
