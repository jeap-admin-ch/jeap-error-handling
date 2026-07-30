import {Component, inject} from '@angular/core';
import {LangChangeEvent, TranslateService} from '@ngx-translate/core';
import {ObMasterLayoutService} from '@oblique/oblique';
import {QdAuthenticationService} from '@quadrel-enterprise-ui/auth';
import {VersionDetectorService} from './shared/version-detector.service';

@Component({
	selector: 'app-root',
	templateUrl: './app.component.html',
	standalone : false
})
export class AppComponent {

	navigation = [
		{url: 'error-list', label: 'i18n.routes.error-list.title'},
		{url: 'error-group', label: 'i18n.routes.error-group.title'},
		{url: 'reactivate-dead-letter', label: 'i18n.routes.reactivate-dead-letter.title'}
	];

	private readonly masterLayoutService = inject(ObMasterLayoutService);

	constructor(private readonly authenticationService: QdAuthenticationService,
				private readonly translate: TranslateService,
				private readonly versionDetectorService: VersionDetectorService) {

		this.masterLayoutService.header.loginState$.subscribe($event => this.loginStatus($event));
	}

	loginStatus($event) {
		// Oblique reports an undefined login state when the ePortal backend cannot be reached. Forwarding it
		// would make the authentication service fail while reading the PAMS session status.
		if ($event !== undefined && $event !== null) {
			this.authenticationService.pamsStatus.next($event);
		}
	}

	languageChange(lang: string) {
		this.translate.use(lang);
	}

	getVersion() {
		return this.versionDetectorService.getVersion();
	}
}
