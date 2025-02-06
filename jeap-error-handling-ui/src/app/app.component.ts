import {Component, inject} from '@angular/core';
import {LangChangeEvent, TranslateService} from '@ngx-translate/core';
import {ObMasterLayoutService} from '@oblique/oblique';
import {QdAuthenticationService} from '@quadrel-services/qd-auth';
import {VersionDetectorService} from './shared/version-detector.service';

@Component({
	selector: 'app-root',
	templateUrl: './app.component.html'
})
export class AppComponent {

	navigation = [
		{url: 'error-list', label: 'i18n.routes.error-list.title'},
		{url: 'error-group', label: 'i18n.routes.error-group.title'},
		{url: 'reactivate-dead-letter', label: 'i18n.routes.reactivate-dead-letter.title'}
	];

	private readonly translateService = inject(TranslateService);

	private readonly masterLayoutService = inject(ObMasterLayoutService);

	constructor(private readonly authenticationService: QdAuthenticationService,
				private readonly translate: TranslateService,
				private readonly versionDetectorService: VersionDetectorService) {

		this.translateService.onLangChange.subscribe(({lang}: LangChangeEvent) => this.languageChange(lang));
		this.masterLayoutService.header.loginState$.subscribe($event => this.loginStatus($event));
	}

	loginStatus($event) {
		this.authenticationService.pamsStatus.next($event);
	}

	languageChange(lang: string) {
		this.translate.use(lang);
	}

	getVersion() {
		return this.versionDetectorService.getVersion();
	}
}
