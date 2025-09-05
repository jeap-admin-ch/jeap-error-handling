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
	freeText: string;
	stackTraceHash: string;
}

export interface ErrorGroupResponse {
	totalErrorGroupCount: number;
	groups: ErrorGroupDTO[];
}
