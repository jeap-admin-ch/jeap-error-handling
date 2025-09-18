import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpErrorResponse, HttpParams} from '@angular/common/http';
import {Observable, throwError} from 'rxjs';
import {environment} from '../../../environments/environment';
import {ErrorDTO, ErrorGroupDetailsListSearchFormDto, ErrorListDTO, ErrorSearchFormDto} from './error.model';
import {catchError} from 'rxjs/operators';
import {DialogService} from "../dialog/dialog.service";
import {NotifierService} from "../notifier/notifier.service";
import {TranslateService} from "@ngx-translate/core";
import {Sort} from "@angular/material/sort";


/**
 * Service for handling error-related operations such as searching, deleting, and retrying errors.
 * Integrates with backend API, dialog, notification, and translation services.
 */
@Injectable({
	providedIn: 'root'
})
export class ErrorService {
	private readonly http = inject(HttpClient);
	private readonly dialogService = inject(DialogService);
	private readonly notifierService = inject(NotifierService);
	private readonly translateService = inject(TranslateService);

	private static readonly url: string = environment.BACKEND_SERVICE_API + '/error';

	/**
	 * Deletes a single error row after user confirmation via dialog.
	 * @param row The error row to delete.
	 * @param reloadCallback Callback to reload data after successful deletion.
	 * @param notifyFailure Callback to notify on failure.
	 */
	deleteRowWithDialog(row: ErrorDTO,
						reloadCallback: () => void,
						notifyFailure: (error: any) => void) {
		this.dialogService.getClosingReason().subscribe(reason => {
			if (reason != null) {
				this.delete(row.id, reason).subscribe(
					() => {
						reloadCallback();
						this.notifierService.notifySuccess('i18n.errorhandling.action.delete', 'i18n.errorhandling.action.success')();
					},
					(error) => {
						notifyFailure(error);
					}
				);
			}
		});
	}

	/**
	 * Deletes multiple selected errors after user confirmation and reason input.
	 * @param selectedErrors Array of errors to delete.
	 * @param reloadCallback Callback to reload data after successful deletion.
	 * @param failureCallback Callback to notify on failure.
	 */
	massDeleteWithDialog(
		selectedErrors: ErrorDTO[],
		reloadCallback: () => void,
		failureCallback: (error: any) => void
	) {
		const count = selectedErrors.length;
		const message = this.translateService.instant('i18n.errorhandling.confirm', {count});

		this.dialogService.confirm(message).subscribe(confirmed => {
			if (confirmed) {
				this.dialogService.getClosingReason().subscribe(reason => {
					if (reason != null) {
						const errorIds: string[] = selectedErrors.map(error => error.id);
						this.massDelete(errorIds, reason).subscribe(
							() => {
								reloadCallback();
								this.notifierService.notifySuccess('i18n.errorhandling.action.delete', 'i18n.errorhandling.action.success')();
							},
							(errorMessage) => {
								failureCallback(errorMessage);
							}
						);
					}
				});
			}
		});
	}

	/**
	 * Retries sending a single error after confirmation if it is deleted.
	 * @param row The error row to retry.
	 * @param reloadCallback Callback to reload data after successful retry.
	 * @param notifyFailure Callback to notify on failure.
	 */
	resendRowWithDialog(row: ErrorDTO,
						reloadCallback: () => void,
						notifyFailure: (error: any) => void) {
		if (row.errorState.includes('DELETED')) {
			this.dialogService.confirm(
				this.translateService.instant('i18n.errorhandling.confirm.closed-error')).subscribe(confirmed => {
				if (confirmed) {
					this.resendEntry(row.id, reloadCallback, notifyFailure);
				}
			});
		} else {
			this.resendEntry(row.id, reloadCallback, notifyFailure);
		}

	}

	/**
	 * Retries sending multiple selected errors after user confirmation.
	 * @param selectedErrors Array of errors to retry.
	 * @param reloadCallback Callback to reload data after successful retry.
	 * @param failureCallback Callback to notify on failure.
	 */
	massRetryWithDialog(
		selectedErrors: ErrorDTO[],
		reloadCallback: () => void,
		failureCallback: (error: any) => void
	) {
		const message = selectedErrors.some(e => e.errorState.includes('DELETED'))
			? this.translateService.instant('i18n.errorhandling.confirm.closed-errors')
			: this.translateService.instant('i18n.errorhandling.confirm', { count: selectedErrors.length });

		this.dialogService.confirm(message).subscribe(confirmed => {
			if (confirmed) {
				const errorIds: string[] = selectedErrors.map(error => error.id);
				this.massRetry(errorIds).subscribe(
					() => {
						reloadCallback();
						this.notifierService.notifySuccess('i18n.errorhandling.action.retry', 'i18n.errorhandling.action.success')();
					},
					(error) => {
						failureCallback(error);
					}
				);
			}
		});
	}

	/**
	 * Helper to retry a single error entry.
	 * @param id Error ID to retry.
	 * @param reloadCallback Callback to reload data after successful retry.
	 * @param notifyFailure Callback to notify on failure.
	 * @private
	 */
	private resendEntry(id: string,
						reloadCallback: () => void,
						notifyFailure: (error: any) => void) {
		this.retry(id).subscribe(
			() => {
				reloadCallback();
				this.notifierService.notifySuccess('i18n.errorhandling.action.retry', 'i18n.errorhandling.action.success')();
			},
			(error) => {
				notifyFailure(error);
			}
		);
	}

	/**
	 * Finds errors by filter criteria with pagination.
	 * @param pageIndex Index of the page.
	 * @param pageSize Size of the page.
	 * @param errorSearchCriteriaDto Search criteria DTO.
	 * @returns Observable of ErrorListDTO.
	 */
	findErrorsByFilter(pageIndex: number, pageSize: number, errorSearchCriteriaDto: ErrorSearchFormDto): Observable<ErrorListDTO> {
		const requestUrl = `${ErrorService.url}/?pageIndex=${pageIndex}&pageSize=${pageSize}`;
		return this.http.post<ErrorListDTO>(requestUrl, errorSearchCriteriaDto).pipe(
			catchError(ErrorService.errorHandler)
		);
	}

	/**
	 * Finds errors by error group ID and search criteria with pagination.
	 * @param errorGroupId Error group ID.
	 * @param searchCriteria Search criteria DTO.
	 * @param pageIndex Index of the page.
	 * @param pageSize Size of the page.
	 * @param sortState Sort state for sorting the results.
	 * @returns Observable of ErrorListDTO.
	 */
	findErrorsByErrorGroupIdAndCriteria(errorGroupId: string,
										searchCriteria: ErrorGroupDetailsListSearchFormDto,
										pageIndex: number, pageSize: number,
										sortState: Sort): Observable<ErrorListDTO> {
		let params = new HttpParams()
			.set('errorGroupId', errorGroupId)
			.set('pageIndex', pageIndex.toString())
			.set('pageSize', pageSize.toString());


		if (searchCriteria == undefined) {
			searchCriteria = {
				dateFrom: null,
				dateTo: null,
				stacktracePattern: null,
				messagePattern: null,
				sortField: sortState.active ?? 'created',
				sortOrder: sortState.direction ?? 'DESC'
			};
		} else {
			searchCriteria.sortField = sortState.active ?? 'created';
			searchCriteria.sortOrder = sortState.direction?.toUpperCase() ?? 'DESC';
		}


		console.log("## Search Criteria:", searchCriteria);


		const requestUrl = `${ErrorService.url}/group`;
		return this.http.post<ErrorListDTO>(requestUrl, searchCriteria, {params}).pipe(
			catchError(ErrorService.errorHandler)
		);
	}

	/**
	 * Return a List of all EventSources
	 */
	getAllEventSources(): Observable<String[]> {
		const requestUrl = `${ErrorService.url}/eventsources`;
		return this.http.get<String[]>(requestUrl).pipe(
			catchError(ErrorService.errorHandler)
		);
	}

	/**
	 * Return a List of all EventNames
	 */
	getAllEventNames(): Observable<string[]> {
		const requestUrl = `${ErrorService.url}/eventnames`;
		return this.http.get<string[]>(requestUrl).pipe(
			catchError(ErrorService.errorHandler)
		);
	}

	/**
	 * Return a List of all ErrorCodes
	 */
	getAllErrorCodes(): Observable<String[]> {
		const requestUrl = `${ErrorService.url}/errorcodes`;
		return this.http.get<String[]>(requestUrl).pipe(
			catchError(ErrorService.errorHandler)
		);
	}

	getErrorDetails(errorId: string): Observable<ErrorDTO> {
		const requestUrl = `${ErrorService.url}/${errorId}/details`;
		return this.http.get<ErrorDTO>(requestUrl).pipe(
			catchError(ErrorService.errorHandler)
		);
	}

	getCausingEventPayload(errorId: string): Observable<string> {
		const requestUrl = `${ErrorService.url}/${errorId}/event/payload`;
		return this.http.get(requestUrl, {responseType: 'text'}).pipe(
			catchError(ErrorService.errorHandler)
		);
	}

	/**
	 * Retries a specific error event.
	 * @param errorId The ID of the error.
	 * @returns Observable of any.
	 */
	retry(errorId: string): Observable<any> {
		const requestUrl = `${ErrorService.url}/${errorId}/event/retry`;
		return this.http.post(requestUrl, null).pipe(
			catchError(ErrorService.errorHandler)
		);
	}

	/**
	 * Retries multiple error events.
	 * @param errorIds Array of error IDs.
	 * @returns Observable of any.
	 */
	massRetry(errorIds: string[]) {
		const requestUrl = `${ErrorService.url}/event/retry`;
		return this.http.post(requestUrl, errorIds).pipe(
			catchError(ErrorService.errorHandler)
		);
	}

	/**
	 * Deletes a specific error with an optional reason.
	 * @param errorId The ID of the error.
	 * @param reason Optional reason for deletion.
	 * @returns Observable of any.
	 */
	delete(errorId: string, reason?: string): Observable<any> {
		const requestUrl = `${ErrorService.url}/${errorId}?reason=${reason}`;
		return this.http.delete(requestUrl).pipe(
			catchError(ErrorService.errorHandler)
		);
	}

	/**
	 * Deletes multiple errors with a reason.
	 * @param errorIds Array of error IDs.
	 * @param reason Reason for deletion.
	 * @returns Observable of any.
	 */
	massDelete(errorIds: string[], reason: string) {
		const requestUrl = `${ErrorService.url}/delete?reason=${reason}`;
		return this.http.post(requestUrl, errorIds).pipe(
			catchError(ErrorService.errorHandler)
		);
	}

	/**
	 * Handles HTTP errors and returns a formatted error message.
	 * @param response The HTTP error response.
	 * @returns Observable that throws an error message.
	 * @private
	 */
	private static errorHandler(response: HttpErrorResponse): Observable<never> {
		let errorMessage;
		if (response.error instanceof ErrorEvent) {
			errorMessage = response.error.message;
		} else if (response.status === 0) {
			errorMessage = response.statusText;
		} else {
			const error = response.error;
			errorMessage = (error ? error.error + ' ' : '') + response.status;
		}
		return throwError(errorMessage);
	}

}
