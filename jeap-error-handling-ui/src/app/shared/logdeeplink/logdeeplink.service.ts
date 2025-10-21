import {Injectable} from '@angular/core';
import {environment} from '../../../environments/environment';
import {Observable} from 'rxjs';
import {HttpClient} from '@angular/common/http';

@Injectable({
	providedIn: 'root'
})
export class LogDeepLinkService {

	private static readonly url: string = environment.BACKEND_SERVICE_API + '/configuration/log-deeplink';

	constructor(private readonly http: HttpClient) { }

	getLogDeepLink(): Observable<string> {
		return this.http.get(LogDeepLinkService.url, {responseType: 'text'});
	}

	replaceTraceId(encodedUrl: string, traceId: string): string {
		const decoded = decodeURIComponent(encodedUrl);
		return decoded.replace('{traceId}', traceId);
	}
}
