package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventIdentity;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventPublisher;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventType;
import ch.admin.bit.jeap.errorhandling.event.test.TestEvent;
import ch.admin.bit.jeap.errorhandling.event.test.TestPayload;
import ch.admin.bit.jeap.errorhandling.event.test.TestReferences;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.*;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.security.test.mock.OidcAuthorizationMockServer;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.awaitility.Awaitility.await;

/**
 * Base class for browser end-to-end tests driving the Angular UI bundled into the service with Playwright.
 * <p>
 * The Spring Boot app is started by {@link ErrorHandlingITBase} on port 8303 and serves the UI as static
 * resources. The servlet context path is set to /error-handling to match the base-href the UI is built with.
 * Authentication runs the real OIDC authorization code flow in the browser against an
 * {@link OidcAuthorizationMockServer}: the app is configured with auto-login, so navigating to a guarded
 * route redirects to the mock authorization server, which immediately issues an authorization code for the
 * configured mock user. The mock user's roles are switched per test via the mock server's role profiles.
 */
@Slf4j
@TestPropertySource(properties = {
        "server.servlet.context-path=/error-handling",
        "jeap.errorhandling.frontend.token-aware-pattern[0]=/error-handling/api/",
        "jeap.errorhandling.frontend.redirect-url=http://localhost:8303/error-handling/redirect",
        "jeap.errorhandling.frontend.auto-login=true",
        // the resource server validates the token audience against the application name
        "jeap.errorhandling.frontend.client-id=jeap-error-handling-service",
        // mirroring the topic wiring of ErrorHandlingIT: the failures of the failing TestConsumer are
        // published to the topic the error handling service consumes from, its own failures to the dead
        // letter topic, see TestConsumerErrorTopicConfiguration
        "jeap.errorhandling.deadLetterTopicName=" + ErrorHandlingITBase.DEAD_LETTER_TOPIC,
        "jeap.errorhandling.topic=" + ErrorHandlingITBase.ERROR_TOPIC
})
@ActiveProfiles(TestConsumerErrorTopicConfiguration.PROFILE)
public abstract class UiBrowserTestBase extends ErrorHandlingITBase {

    protected static final String APP_URL = "http://localhost:8303/error-handling/";
    protected static final String SUBJECT = "69368608-D736-43C8-5F76-55B7BF168299";
    protected static final String MODULITH_LISTENER = "example.order.OrderPlacedListener";
    protected static final String MODULITH_EVENT_TYPE = "example.order.OrderPlacedEvent";
    protected static final String MODULITH_CONTENT_TYPE = "application/json";
    // must match the application name, as the resource server validates the token audience against it
    private static final String CLIENT_ID = "jeap-error-handling-service";

    private static final String OAUTH_MOCK_BASE_PATH = "/oidc-mock";
    private static final int OAUTH_MOCK_PORT = 8305;

    // the ePortal service navigation of Oblique talks to https://pams-api.eportal<env>.admin.ch and redirects
    // to https://eportal<env>.admin.ch
    private static final Pattern EPORTAL_REQUEST_PATTERN = Pattern.compile("eportal|pams", Pattern.CASE_INSENSITIVE);

    protected static final String ERROR_VIEW_ROLE = "jme_@error_#view";
    protected static final String ERROR_RETRY_ROLE = "jme_@error_#retry";
    protected static final String ERROR_DELETE_ROLE = "jme_@error_#delete";
    protected static final String ERRORGROUP_VIEW_ROLE = "jme_@errorgroup_#view";
    protected static final String ERRORGROUP_EDIT_ROLE = "jme_@errorgroup_#edit";
    // a role granting no permissions in this application; the mock server requires at least one role
    protected static final String UNRELATED_ROLE = "jme_@other_#none";
    protected static final List<String> ALL_ROLES =
            List.of(ERROR_VIEW_ROLE, ERROR_RETRY_ROLE, ERROR_DELETE_ROLE, ERRORGROUP_VIEW_ROLE, ERRORGROUP_EDIT_ROLE);
    protected static final List<String> VIEW_ONLY_ROLES = List.of(ERROR_VIEW_ROLE);
    protected static final List<String> UNRELATED_ROLES = List.of(UNRELATED_ROLE);

    private static final String VIEW_ONLY_PROFILE = "view-only";
    private static final String UNRELATED_PROFILE = "unrelated";
    private static final Map<List<String>, String> PROFILE_NAMES_BY_ROLES = Map.of(
            ALL_ROLES, "default",
            VIEW_ONLY_ROLES, VIEW_ONLY_PROFILE,
            UNRELATED_ROLES, UNRELATED_PROFILE);

    // the mock authorization server runs once per JVM with a stable signing key; the mock user's roles are
    // switched per test via role profiles (the application caches the JWKS per issuer, so the signing key
    // must never change while the application context is running)
    private static OidcAuthorizationMockServer oauthMockServer;

    private static Playwright playwright;
    private static Browser browser;

    @Autowired
    private KafkaProperties kafkaProperties;
    @Autowired
    private CacheManager cacheManager;

    protected BrowserContext context;
    protected Page page;

    /**
     * Requests the browser sent to the ePortal/PAMS backend of the Oblique service navigation, recorded for
     * the currently open page. Empty as long as the PAMS integration is disabled, see
     * {@code jeap.errorhandling.frontend.pams-enabled}.
     */
    protected final List<String> ePortalRequests = Collections.synchronizedList(new ArrayList<>());

    @DynamicPropertySource
    static void oauthMockServerProperties(DynamicPropertyRegistry registry) {
        // validate tokens against the mock authorization server; the ConfigurationController also serves
        // the issuer as OIDC authority to the frontend
        String issuer = "http://localhost:" + OAUTH_MOCK_PORT + OAUTH_MOCK_BASE_PATH;
        registry.add("jeap.security.oauth2.resourceserver.authorization-server.issuer", () -> issuer);
        registry.add("jeap.security.oauth2.resourceserver.authorization-server.jwk-set-uri",
                () -> issuer + "/.well-known/jwks.json");
    }

    @BeforeAll
    static void startBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setChannel("chrome"));
    }

    @AfterAll
    static void stopBrowser() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }

    @BeforeEach
    void openAuthenticatedPage() {
        // the dropdowns for event sources / error codes / event names are cached - make sure freshly seeded
        // test data is visible to each test
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
        openPageWithRoles(ALL_ROLES);
    }

    @AfterEach
    void closePage() {
        closeCurrentContext();
    }

    /**
     * (Re-)opens a browser context and page for a mock user with the given roles. Tests call this directly
     * to run with a restricted set of roles ({@link #VIEW_ONLY_ROLES}, {@link #UNRELATED_ROLES}), the default
     * is {@link #ALL_ROLES}. The roles are switched via the role profiles of the mock authorization server,
     * so the next login yields tokens with the requested role set.
     */
    protected void openPageWithRoles(List<String> roles) {
        String profileName = PROFILE_NAMES_BY_ROLES.get(roles);
        if (profileName == null) {
            throw new IllegalArgumentException("No mock server role profile defined for roles " + roles);
        }
        ensureMockServerStarted();
        oauthMockServer.reset();
        oauthMockServer.setActiveProfile(profileName);
        closeCurrentContext();
        ePortalRequests.clear();
        context = browser.newContext(new Browser.NewContextOptions().setLocale("en-US"));
        page = context.newPage();
        page.setDefaultTimeout(20_000);
        PlaywrightAssertions.setDefaultAssertionTimeout(15_000);
        page.onConsoleMessage(message -> log.info("Browser console: {}: {}", message.type(), message.text()));
        page.onPageError(error -> log.warn("Browser page error: {}", error));
        page.onResponse(response -> {
            if (response.url().contains("/api/") || response.url().contains(OAUTH_MOCK_BASE_PATH)
                    || response.status() >= 400) {
                log.info("Browser response: {} {} {}", response.status(), response.request().method(), response.url());
            }
        });
        page.onRequest(request -> {
            if (request.url().contains(OAUTH_MOCK_BASE_PATH)) {
                log.info("Browser request to OAuth mock: {} {}", request.method(), request.url());
            }
            if (EPORTAL_REQUEST_PATTERN.matcher(request.url()).find()) {
                log.info("Browser request to ePortal/PAMS: {} {}", request.method(), request.url());
                ePortalRequests.add(request.url());
            }
        });
        page.onFrameNavigated(frame -> {
            if (frame == page.mainFrame()) {
                log.info("Browser navigated to: {}", frame.url());
            }
        });
    }

    private static synchronized void ensureMockServerStarted() {
        if (oauthMockServer != null) {
            return;
        }
        OidcAuthorizationMockServer mockServer = OidcAuthorizationMockServer
                .builder(OAUTH_MOCK_PORT, OAUTH_MOCK_BASE_PATH, "http://localhost:8303")
                .withDefaultClientId(CLIENT_ID)
                .withSubject(SUBJECT)
                .withGivenName("E2E")
                .withFamilyName("Testuser")
                .withName("E2E Testuser")
                .withUserRoles(ALL_ROLES)
                .withRoleProfile(VIEW_ONLY_PROFILE, VIEW_ONLY_ROLES)
                .withRoleProfile(UNRELATED_PROFILE, UNRELATED_ROLES)
                .build();
        mockServer.start();
        oauthMockServer = mockServer;
    }

    private void closeCurrentContext() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    /**
     * Clicks the submit button of the currently open confirmation or closing reason dialog.
     */
    protected void confirmDialog() {
        page.getByRole(AriaRole.DIALOG).getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(UiLabels.confirm())).click();
    }

    /**
     * The data rows of the error list table.
     */
    protected Locator errorListRows() {
        return page.getByTestId("error-list-row");
    }

    /**
     * The details link of the given error. Errors produced by retrying a causing event are textually identical
     * to the original error, so the details link href carrying the error ID is the only stable discriminator.
     */
    protected Locator errorDetailsLink(Error error) {
        return page.locator("a[href$='/error-details/" + error.getId() + "']");
    }

    // --- test data seeding -------------------------------------------------------------------------------

    /**
     * Publishes a real Avro TestEvent to the domain event topic. The embedded TestConsumer fails permanently
     * on it, so the error handling service records a permanent error with the original Avro payload - the
     * same end-to-end path as in production.
     */
    protected TestEvent publishPermanentErrorTestEvent() {
        TestEvent testEvent = TestEvent.newBuilder()
                .setType(AvroDomainEventType.newBuilder()
                        .setName("TestEvent")
                        .setVersion("1")
                        .build())
                .setReferences(TestReferences.newBuilder().build())
                .setDomainEventVersion("1.0.0")
                .setIdentity(AvroDomainEventIdentity.newBuilder()
                        .setEventId(UUID.randomUUID().toString())
                        .setIdempotenceId(UUID.randomUUID().toString())
                        .setCreated(Instant.now())
                        .build())
                .setPayload(TestPayload.newBuilder()
                        .setMessage(PERMANENT_ERROR)
                        .build())
                .setPublisher(AvroDomainEventPublisher.newBuilder()
                        .setSystem("TEST")
                        .setService("jeap-error-handling-service")
                        .build())
                .build();
        kafkaTemplate.send(DOMAIN_EVENT_TOPIC, testEvent);
        return testEvent;
    }

    protected List<Error> awaitErrorsInRepository(int numErrors) {
        await(numErrors + " errors have been recorded in repository").atMost(FORTY_SECONDS)
                .until(() -> errorRepository.findAll().size() == numErrors);
        return errorRepository.findAll();
    }

    protected ErrorGroup errorGroup(String stackTraceHash, String ticketNumber) {
        ErrorGroup errorGroup = new ErrorGroup("123", "eventName", "service", "message", stackTraceHash);
        if (ticketNumber != null) {
            errorGroup.setTicketNumber(ticketNumber);
        }
        return errorGroupRepository.save(errorGroup);
    }

    protected Error saveError(String errorMessage, ErrorGroup errorGroup) {
        return saveError(errorMessage, errorGroup, "123", "service", ZonedDateTime.now());
    }

    protected Error saveError(String errorMessage, ErrorGroup errorGroup, String errorCode, String serviceName,
                              ZonedDateTime created) {
        EventMetadata metadata = eventMetadata(serviceName, created);
        CausingEvent causingEvent = CausingEvent.builder()
                .message(EventMessage.builder()
                        .offset(1)
                        .payload("test".getBytes(StandardCharsets.UTF_8))
                        .topic("topic")
                        // use a known cluster name so that the resend cluster can be resolved in the details view
                        .clusterName(kafkaProperties.getDefaultProducerClusterName())
                        .build())
                .metadata(metadata)
                .build();
        causingEventRepository.save(causingEvent);
        Error error = Error.builder()
                .state(Error.ErrorState.PERMANENT)
                .causingEvent(causingEvent)
                .errorEventData(ErrorEventData.builder()
                        .code(errorCode)
                        .temporality(ErrorEventData.Temporality.PERMANENT)
                        .message(errorMessage)
                        .stackTrace("stacktrace")
                        .build())
                .errorEventMetadata(metadata)
                .originalTraceContext(OriginalTraceContext.builder()
                        .traceIdString("traceId")
                        .build())
                .created(created)
                .build();
        if (errorGroup != null) {
            error.setErrorGroup(errorGroup);
        }
        return errorRepository.save(error);
    }

    /**
     * Saves a permanent error for a failed Spring Modulith publication, as the error handling service records it
     * after having consumed a ModulithPublicationProcessingFailedEvent. Seeded through the repositories rather than
     * over Kafka, so that the browser tests do not have to configure the optional Modulith failure topic - the
     * ingestion path itself is covered by {@link ModulithPublicationErrorHandlingIT}.
     *
     * @param serializedEvent the publication payload as the publishing application stored it, shown verbatim in the
     *                        details view
     */
    protected Error saveModulithError(String errorMessage, String publicationId, String serializedEvent) {
        EventMetadata metadata = eventMetadata("order-service", ZonedDateTime.now());
        CausingEvent causingEvent = causingEventRepository.save(CausingEvent.builder()
                .origin(CausingEvent.Origin.MODULITH_PUBLICATION)
                .metadata(metadata)
                .modulithPublication(ModulithPublicationData.builder()
                        // use a known cluster name so that the retry and discard commands can be routed to the
                        // transactional outbox of that cluster
                        .clusterName(kafkaProperties.getDefaultProducerClusterName())
                        .publicationId(publicationId)
                        .listener(MODULITH_LISTENER)
                        .eventType(MODULITH_EVENT_TYPE)
                        .serializedEvent(serializedEvent.getBytes(StandardCharsets.UTF_8))
                        .serializedEventContentType(MODULITH_CONTENT_TYPE)
                        .retryCommandTopic("modulith-retry-command-topic")
                        .discardCommandTopic("modulith-discard-command-topic")
                        .build())
                .build());
        return errorRepository.save(Error.builder()
                .state(Error.ErrorState.PERMANENT)
                .causingEvent(causingEvent)
                .errorEventData(ErrorEventData.builder()
                        .code("MODULITH_PUBLICATION_PROCESSING_FAILED")
                        .temporality(ErrorEventData.Temporality.PERMANENT)
                        .message(errorMessage)
                        .stackTrace("stacktrace")
                        .build())
                .errorEventMetadata(metadata)
                .originalTraceContext(OriginalTraceContext.builder()
                        .traceIdString("traceId")
                        .build())
                .created(ZonedDateTime.now())
                .closingReason("")
                .build());
    }

    private static EventMetadata eventMetadata(String serviceName, ZonedDateTime created) {        return EventMetadata.builder()
                .id(UUID.randomUUID().toString())
                .created(created)
                .idempotenceId(UUID.randomUUID().toString())
                .publisher(EventPublisher.builder()
                        .service(serviceName)
                        .system("system")
                        .build())
                .type(EventType.builder()
                        .name("eventName")
                        .version("1.0.0")
                        .build())
                .build();
    }
}
