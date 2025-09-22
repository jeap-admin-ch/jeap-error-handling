package ch.admin.bit.jeap.errorhandling.infrastructure.jira;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = MediaType.APPLICATION_JSON_VALUE, contentType = MediaType.APPLICATION_JSON_VALUE)
interface JiraRestClient {

    @PostExchange("/rest/api/2/issue")
    JiraCreateIssueResponse createIssue(@RequestBody JiraCreateIssueRequest request);

}

