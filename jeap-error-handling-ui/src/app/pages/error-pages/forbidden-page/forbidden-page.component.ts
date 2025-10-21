import {Component} from '@angular/core';
import {HttpStatusCode} from '@angular/common/http';
import {TranslateService} from '@ngx-translate/core';

@Component({
	selector: 'app-forbidden-page',
	styleUrls: ['./forbidden-page.component.scss'],
	templateUrl: './forbidden-page.component.html',
	standalone: false
})
export class ForbiddenPageComponent {
	httpStatusCode = HttpStatusCode.Forbidden;

	constructor(readonly translate: TranslateService) {
	}

}
