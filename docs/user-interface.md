# User Interface

The Error Handling Service ships with an Angular UI that is built during the Maven build and served by the
Spring Boot backend under the base path `/error-handling/`. It is secured with OAuth2/OIDC (see
[Configuration](configuration.md#frontend-and-oauth)).

## Roles

Access to the UI and its actions is controlled with jEAP semantic roles:

| Role                             | Allows                                                        |
|----------------------------------|---------------------------------------------------------------|
| `<systemname>_@error_#view`      | Viewing errors (required to use the UI at all).               |
| `<systemname>_@error_#retry`     | Resending causing messages to their consumers.                |
| `<systemname>_@error_#delete`    | Deleting (logically closing / ignoring) errors.               |
| `<systemname>_@errorgroup_#view` | Viewing error groups.                                         |
| `<systemname>_@errorgroup_#edit` | Editing the free text and Jira ticket number of error groups. |

The backend checks `hasRole(..)`, i.e. it is sufficient for a user to have the role for any business
partner. Typically the roles are assigned as user roles for the internal business partner.

## Error list

The error list is the main view and shows errors filtered by state — by default the permanent errors that
need attention. Available filters: date range, message type, trace ID, event ID, stack trace (regular
expression), source service, error state, error code, Jira ticket number, and a **"no Jira ticket"**
checkbox that hides all errors whose [error group](error-groups.md) already has a ticket assigned.

The defaults of the state filter and the "no Jira ticket" filter are
[configurable](configuration.md#error-list-view-defaults). The following view settings are persisted per
user in the browser's local storage and take precedence over the configured defaults:

- the "no Jira ticket" filter,
- the selected error state filter,
- the sort column and direction,
- the page size.

The reset button drops the persisted settings and returns to the configured defaults. Rows can be selected
for mass retry and mass delete; deleting asks for an optional closing reason that is stored on the error.

## Error details

The details page of an error shows the failure data (error message, code, temporality, stack trace) and its
origin. Kafka errors show message metadata (type, publisher, topic, partition and offset, cluster) and the
**payload of the causing message deserialized to JSON** — if the payload is not valid Avro (or encrypted), a
fallback message is displayed instead. Modulith errors show the publication ID, listener, internal event type,
payload content type and the serialized internal event. The details page offers retry and delete/ignore for
Kafka errors, or retry publication and discard publication for Modulith errors, as well as copy stack trace and a
deep link into the log system based on the trace ID (see
[Configuration](configuration.md#log-deep-link)).

All retry and delete actions are recorded in an audit log with the acting user (from the JWT token claims)
and shown on the details page.

## Error groups

The group view lists [error groups](error-groups.md) with their error count, message type, source, error
code, stack trace hash, first/latest occurrence and Jira ticket. The group details page allows editing the
free text and the ticket number, creating a Jira ticket (with configured
[Jira integration](configuration.md#jira-issue-tracking)) and jumping to the ticket in the ticketing system.
The default sorting is configurable and locally overridable per user.

## Related

- [Error Groups](error-groups.md) — grouping concept and roles
- [Configuration](configuration.md) — frontend, OAuth and view default properties
- [Development](development.md) — running the UI locally, browser end-to-end tests
