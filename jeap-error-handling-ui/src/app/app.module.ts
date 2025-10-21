import {BrowserModule} from '@angular/platform-browser';
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import {CUSTOM_ELEMENTS_SCHEMA, NgModule} from '@angular/core';
import {provideHttpClient, withInterceptorsFromDi} from '@angular/common/http';
import {TranslateModule} from '@ngx-translate/core';
import {
	OB_PAMS_CONFIGURATION,
	ObButtonModule,
	ObColumnLayoutModule,
	ObDocumentMetaService,
	ObEPamsEnvironment,
	ObMasterLayoutConfig,
	ObMasterLayoutModule,
	ObNotificationModule, provideObliqueConfiguration
} from '@oblique/oblique';
import {MatAutocompleteModule} from '@angular/material/autocomplete';
import {MatBadgeModule} from '@angular/material/badge';
import {MatButtonModule} from '@angular/material/button';
import {MatButtonToggleModule} from '@angular/material/button-toggle';
import {MatCardModule} from '@angular/material/card';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MAT_DATE_LOCALE, MatNativeDateModule} from '@angular/material/core';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatDialogModule} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatListModule} from '@angular/material/list';
import {MatMenuModule} from '@angular/material/menu';
import {MatPaginatorModule} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatRadioModule} from '@angular/material/radio';
import {MatSelectModule} from '@angular/material/select';
import {MatSlideToggleModule} from '@angular/material/slide-toggle';
import {MatSliderModule} from '@angular/material/slider';
import {MatSortModule} from '@angular/material/sort';
import {MatTableModule} from '@angular/material/table';
import {MatTabsModule} from '@angular/material/tabs';
import {MatTooltipModule} from '@angular/material/tooltip';
import {AppComponent} from './app.component';
import {AppRoutingModule} from './app-routing.module';

import {ErrorListPageComponent} from './pages/error-list-page/error-list-page.component';
import {ErrorListComponent} from './error-list/error-list.component';
import {ErrorDetailsPageComponent} from './pages/error-details-page/error-details-page.component';
import {ErrorDetailsComponent} from './error-details/error-details.component';
import {CommonModule} from '@angular/common';
import {QdAuthModule, QdConfigService} from '@quadrel-services/qd-auth';
import {appSetup, authConfig} from '../environments/environment';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {ConfirmationDialogComponent} from './shared/dialog/confirmation-dialog/confirmation-dialog.component';
import {ClosingReasonDialogComponent} from './shared/dialog/closing-reason-dialog/closing-reason-dialog.component';
import {ErrorGroupPageComponent} from './pages/error-group-page/error-group-page.component';
import {ErrorGroupsComponent} from './error-groups/error-groups.component';
import {ForbiddenPageComponent} from './pages/error-pages/forbidden-page/forbidden-page.component';
import {ErrorGroupFilterComponent} from './error-groups/error-group-filter/error-group-filter.component';
// import {QdShellHeaderWidgetEnvironment} from '@quadrel-services/qd-auth/lib/config/model/config.model';

export type QdShellHeaderWidgetEnvironment = 'DEV' | 'TEST' | 'REF' | 'ABN' | 'PROD';

@NgModule({
	declarations: [
		AppComponent,
		ErrorListPageComponent,
		ErrorListComponent,
		ErrorDetailsPageComponent,
		ErrorDetailsComponent,
		ConfirmationDialogComponent,
		ClosingReasonDialogComponent,
		ErrorGroupPageComponent,
		ErrorGroupsComponent,
		ClosingReasonDialogComponent,
		ForbiddenPageComponent
	],
	bootstrap: [AppComponent],
	schemas: [CUSTOM_ELEMENTS_SCHEMA],
	imports: [
		BrowserModule,
		BrowserAnimationsModule,
		MatIconModule,
		MatBadgeModule,
		MatButtonModule,
		MatCardModule,
		MatDatepickerModule,
		MatFormFieldModule,
		MatNativeDateModule,
		MatInputModule,
		MatSelectModule,
		MatDialogModule,
		MatPaginatorModule,
		MatProgressBarModule,
		MatRadioModule,
		MatTabsModule,
		MatTableModule,
		MatProgressSpinnerModule,
		MatSortModule,
		MatTooltipModule,
		MatAutocompleteModule,
		MatSliderModule,
		MatSlideToggleModule,
		MatListModule,
		MatCheckboxModule,
		MatMenuModule,
		MatButtonToggleModule,
		CommonModule,
		ObMasterLayoutModule,
		TranslateModule,
		AppRoutingModule,
		QdAuthModule.forRoot(appSetup, authConfig),
		ObColumnLayoutModule,
		ReactiveFormsModule,
		ObButtonModule,
		FormsModule,
		ObNotificationModule,
		ErrorGroupFilterComponent
	],
	providers: [provideObliqueConfiguration({
		accessibilityStatement: {
			createdOn: new Date('2025-10-21'),
			applicationName: 'jeap-error-handling',
			conformity: 'none',
			applicationOperator: 'Bundesamt für Informatik und Telekommunikation (BIT)',
			contact: [{email: 'jeap@bit.admin.ch'}]
		}
	}),

		{provide: MAT_DATE_LOCALE, useValue: 'de-CH'},
		{
			provide: OB_PAMS_CONFIGURATION,
			useFactory: (appModule: AppModule) => appModule.getObPamsEnvironment(),
			deps: [AppModule]
		},
		provideHttpClient(withInterceptorsFromDi())
	]
})

export class AppModule {

	obEPamsEnvironment: ObEPamsEnvironment;

	constructor(readonly documentMetaService: ObDocumentMetaService,
				readonly masterLayoutConfig: ObMasterLayoutConfig,
				readonly configService: QdConfigService,
	) {

		// As the HEAD `title` element and the `description` meta element are outside any
		// Angular entry component, we use a service to update these element values:
		documentMetaService.titleSuffix = 'i18n.application.title';
		documentMetaService.description = 'i18n.application.description';

		masterLayoutConfig.layout.hasMainNavigation = true;

		// Oblique's MasterLayoutComponent configuration
		masterLayoutConfig.locale.locales = ['de', 'fr', 'it', 'en'];
		masterLayoutConfig.locale.defaultLanguage = 'de';
		masterLayoutConfig.header.serviceNavigation.pamsAppId = 'notUsed';
		masterLayoutConfig.header.serviceNavigation.displayInfo = false;
		masterLayoutConfig.header.serviceNavigation.displayLanguages = true;
		masterLayoutConfig.header.serviceNavigation.displayMessage = true;
		masterLayoutConfig.header.serviceNavigation.displayProfile = true;
		masterLayoutConfig.header.serviceNavigation.displayAuthentication = true;
		masterLayoutConfig.header.serviceNavigation.handleLogout = true;

		this.configService.config$.subscribe(qdConfig => {
			if (qdConfig) {
				authConfig.clientId = qdConfig.clientId;
				authConfig.systemName = qdConfig.systemName;
				this.obEPamsEnvironment = this.mapEnvironmentEnum(qdConfig.pamsEnvironment);
			}
		});

	}

	/**
	 * Retrieves the current PAMS environment for the ServiceNavigation in Oblique.
	 * Possible values: "-d", "-r", "-t", "-a", ""
	 *
	 */
	getObPamsEnvironment() {
		return {environment: this.obEPamsEnvironment};
	}

	/**
	 * Maps a given QdShellHeaderWidgetEnvironment string to the corresponding ObEPamsEnvironment enumeration value.
	 */
	private mapEnvironmentEnum(qdEnv: '' | QdShellHeaderWidgetEnvironment): ObEPamsEnvironment {
		switch (qdEnv) {
			case 'DEV':
				return ObEPamsEnvironment.DEV;
			case 'REF':
				return ObEPamsEnvironment.REF;
			case 'ABN':
				return ObEPamsEnvironment.ABN;
			case 'TEST':
				return ObEPamsEnvironment.TEST;
			case 'PROD':
				return ObEPamsEnvironment.PROD;
			default:
				console.warn(`Unrecognized pamsEnvironment: ${qdEnv}. ServiceNavigation will not work.`);
				return null;
		}
	}
}
