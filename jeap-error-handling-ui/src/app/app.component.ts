import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {LangChangeEvent, TranslateService} from '@ngx-translate/core';
import {ObEIcon, ObINavigationLink, ObMasterLayoutService} from '@oblique/oblique';
import {QdAuthenticationService} from '@quadrel-enterprise-ui/auth';
import {Subscription} from 'rxjs';
import {VersionDetectorService} from './shared/version-detector.service';
import {DeadLetterCountService} from './shared/dead-letter-count.service';

@Component({
	selector: 'app-root',
	templateUrl: './app.component.html',
	standalone : false
})
export class AppComponent implements OnInit, OnDestroy {

	navigation: ObINavigationLink[] = this.buildNavigation(null);

	private readonly masterLayoutService = inject(ObMasterLayoutService);
	private pendingDeadLetterCount: number | null = null;
	private readonly subscriptions = new Subscription();
	// guards against the onTranslationChange subscription below reacting to labelWithCount()'s own translate.set()
	// call, which would otherwise rebuild the navigation, call labelWithCount() again, and recurse infinitely
	private settingDynamicLabel = false;

	constructor(private readonly authenticationService: QdAuthenticationService,
				private readonly translate: TranslateService,
				private readonly versionDetectorService: VersionDetectorService,
				private readonly deadLetterCountService: DeadLetterCountService) {

		this.masterLayoutService.header.loginState$.subscribe($event => this.loginStatus($event));
	}

	ngOnInit() {
		this.subscriptions.add(this.deadLetterCountService.count$.subscribe(count => {
			this.pendingDeadLetterCount = count;
			this.navigation = this.buildNavigation(count);
		}));
		// Re-translate the navigation labels when the language changes or the translations finish loading
		// (the initial navigation is built before they may be available)
		const rebuildNavigation = () => this.navigation = this.buildNavigation(this.pendingDeadLetterCount);
		this.subscriptions.add(this.translate.onLangChange.subscribe((_: LangChangeEvent) => rebuildNavigation()));
		this.subscriptions.add(this.translate.onTranslationChange.subscribe(() => {
			if (!this.settingDynamicLabel) {
				rebuildNavigation();
			}
		}));
	}

	ngOnDestroy() {
		this.subscriptions.unsubscribe();
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

	private buildNavigation(pendingDeadLetterCount: number | null): ObINavigationLink[] {
		const reactivateDeadLetterKey = 'i18n.routes.reactivate-dead-letter.title';
		return [
			{url: 'error-list', label: 'i18n.routes.error-list.title'},
			{url: 'error-group', label: 'i18n.routes.error-group.title'},
			{
				url: 'reactivate-dead-letter',
				label: pendingDeadLetterCount > 0 ? this.labelWithCount(reactivateDeadLetterKey, pendingDeadLetterCount) : reactivateDeadLetterKey,
				icon: pendingDeadLetterCount > 0 ? ObEIcon.EXCLAMATION_TRIANGLE : undefined
			}
		];
	}

	/**
	 * ObINavigationLink has no field for interpolation params, and Oblique's nav template re-pipes `label`
	 * through `| translate` - so the count can't be added via the normal translate-with-params idiom (as used
	 * e.g. in error-list.component.html). Registering the resolved, count-suffixed text as its own translation
	 * makes that re-lookup resolve to itself, regardless of which MissingTranslationHandler is configured.
	 */
	private labelWithCount(key: string, count: number): string {
		const label = `${this.translate.instant(key)} (${count})`;
		this.settingDynamicLabel = true;
		try {
			this.translate.set(label, label);
		} finally {
			this.settingDynamicLabel = false;
		}
		return label;
	}
}
