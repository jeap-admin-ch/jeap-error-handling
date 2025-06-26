import {Injectable} from '@angular/core';
import {HttpClient, HttpErrorResponse} from '@angular/common/http';
import {Observable, throwError} from 'rxjs';
import {environment} from '../../../environments/environment';
import {ErrorDTO, ErrorListDTO, ErrorSearchFormDto} from './error.model';
import {catchError} from 'rxjs/operators';

@Injectable({
	providedIn: 'root'
})
export class ErrorService {
	private static readonly url: string = environment.BACKEND_SERVICE_API + '/error';

	constructor(private readonly http: HttpClient) {
	}

	findErrorsByFilter(pageIndex: number, pageSize: number, errorSearchCriteriaDto: ErrorSearchFormDto): Observable<ErrorListDTO> {
		const requestUrl = `${ErrorService.url}/?pageIndex=${pageIndex}&pageSize=${pageSize}`;
		return this.http.post<ErrorListDTO>(requestUrl, errorSearchCriteriaDto).pipe(
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

	retry(errorId: string): Observable<any> {
		const requestUrl = `${ErrorService.url}/${errorId}/event/retry`;
		return this.http.post(requestUrl, null).pipe(
			catchError(ErrorService.errorHandler)
		);
	}

	massRetry(errorIds: string[]) {
		const requestUrl = `${ErrorService.url}/event/retry`;
		return this.http.post(requestUrl, errorIds).pipe(
			catchError(ErrorService.errorHandler)
		);
	}

	delete(errorId: string, reason?: string): Observable<any> {
		const requestUrl = `${ErrorService.url}/${errorId}?reason=${reason}`;
		return this.http.delete(requestUrl).pipe(
			catchError(ErrorService.errorHandler)
		);
	}

	massDelete(errorIds: string[], reason: string) {
		const requestUrl = `${ErrorService.url}/delete?reason=${reason}`;
		return this.http.post(requestUrl, errorIds).pipe(
			catchError(ErrorService.errorHandler)
		);
	}

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
