export interface ErrorDTO {
	timestamp: string;
	id: string;
	errorState: string;
	errorMessage: string;
	errorCode: string;
	errorPublisher: string;
	errorTemporality: string;
	eventName: string;
	eventId: string;
	eventTimestamp: string;
	eventPublisher: string;
	eventTopicDetails: string;
	eventClusterName: string;
	stacktrace: string;
	nextResendTimestamp: string;
	errorCountForEvent: number;
	canRetry: boolean;
	canDelete: boolean;
	originalTraceIdString: string;
	closingReason: string;
	auditLogDTOs: AuditLogDTO[];
	ticketNumber: string;
	freeText: string;
	signed : boolean;
	jeapCert: string;
}

export interface AuditLogDTO {
	authContext: string;
	subject: string;
	action: string;
	created: string;
	extId: string;
	givenName: string;
	familyName: string;
}

export interface ErrorListDTO {
	totalErrorCount: number;
	errors: ErrorDTO[];
}

export interface ErrorSearchFormDto {
	dateFrom: string;
	dateTo: string;
	eventName: string;
	traceId: string;
	eventId: string;
	eventSource: string;
	stacktracePattern: string;
	states: string[];
	errorCode: string;
	sortField: string;
	sortOrder: string;
	closingReason: string;
	ticketNumber: string;
}

export interface ErrorGroupSearchFormDto {
	noTicket: boolean;
	dateFrom: string;
	dateTo: string;
	source: string;
	messageType: string;
	errorCode: string;
	jiraTicket: string;
	sortField: string;
	sortOrder: string;
}

export interface ErrorGroupDTO {
	errorGroupId: string;
	errorCount: number;
	errorEvent: string;
	errorPublisher: string;
	errorCode: string;
	errorMessage: string;
	firstErrorAt: string;
	latestErrorAt: string;
	ticketNumber: string;
	canRetry: boolean;
	canDelete: boolean;
}

export interface ErrorGroupResponse {
	totalErrorGroupCount: number;
	groups: ErrorGroupDTO[];
}


