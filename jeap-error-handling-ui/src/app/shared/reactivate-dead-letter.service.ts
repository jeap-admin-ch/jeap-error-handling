import { Injectable } from '@angular/core';
import {HttpClient, HttpParams, HttpResponse} from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
	providedIn: 'root'
})
export class ReactivateDeadLetterService {

	private readonly apiUrl: string = environment.BACKEND_SERVICE_API + '/deadletter/reactivate';

	constructor(private http: HttpClient) {}

	reactivateDeadLetter(maxRecords: number): Observable<HttpResponse<any>> {
		const params = new HttpParams().set('maxRecords', maxRecords.toString());
		return this.http.post<any>(this.apiUrl, {}, { observe: 'response', params });
	}
}
