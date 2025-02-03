import {Injectable} from "@angular/core";
import {environment} from "../../../environments/environment";
import { HttpClient, HttpParams } from "@angular/common/http";
import {Observable} from "rxjs";
import {ErrorGroupDTO, ErrorGroupResponse} from "./error-group.model";

@Injectable({
	providedIn: 'root'
})
export class ErrorGroupService {
	private static readonly groupUrl: string = environment.BACKEND_SERVICE_API + '/error-group';
	private static readonly url: string = environment.BACKEND_SERVICE_API + '/configuration/ticket-number';

	constructor(private readonly http: HttpClient) {
	}

	getGroups(pageIndex: number = 0, pageSize: number = 10): Observable<ErrorGroupResponse> {
		let params = new HttpParams()
			.set('pageIndex', pageIndex.toString())
			.set('pageSize', pageSize.toString());
		const requestUrl = `${ErrorGroupService.groupUrl}`;
		return this.http.get<ErrorGroupResponse>(requestUrl, {params});
	}

	updateTicketNumber(errorGroupId: string, ticketNumber: string): Observable<ErrorGroupDTO> {
		return this.http.post<ErrorGroupDTO>(`${ErrorGroupService.groupUrl}/update-ticket-number`, {
			errorGroupId,
			ticketNumber
		});
	}

	updateFreeText(errorGroupId: string, freeText: string): Observable<ErrorGroupDTO> {
		return this.http.post<ErrorGroupDTO>(`${ErrorGroupService.groupUrl}/update-free-text`, {
			errorGroupId,
			freeText
		});
	}

	getTicketNumberLink(): Observable<any> {
		return this.http.get(ErrorGroupService.url);
	}
}
