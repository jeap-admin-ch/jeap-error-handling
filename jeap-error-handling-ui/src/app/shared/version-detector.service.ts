import {Injectable} from '@angular/core';
import { HttpClient } from "@angular/common/http";
import {environment} from "../../environments/environment";

@Injectable({
	providedIn: 'root'
})
export class VersionDetectorService {

	private readonly url: string = environment.BACKEND_SERVICE_API + '/configuration/version';

	private version = "";

	constructor(private readonly httpClient: HttpClient) {
		this.httpClient.get(this.url, {responseType: 'text'}).subscribe(value => this.version = value)
	}

	getVersion() {
		return this.version;
	}


}
