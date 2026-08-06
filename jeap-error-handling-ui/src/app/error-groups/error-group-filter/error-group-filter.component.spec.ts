import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ErrorGroupFilterComponent } from './error-group-filter.component';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { provideNativeDateAdapter } from '@angular/material/core';

// Angular Material Module Imports
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatAutocompleteModule } from '@angular/material/autocomplete';

// Oblique
import {ObButtonModule, ObColumnLayoutModule} from '@oblique/oblique';

// Mocks
import { DialogService } from '../../shared/dialog/dialog.service';
import { NotifierService } from '../../shared/notifier/notifier.service';
import { ErrorService } from '../../shared/errorservice/error.service';
import {ErrorGroupsComponent} from "../error-groups.component";

describe('ErrorGroupFilterComponent', () => {
	let component: ErrorGroupFilterComponent;
	let fixture: ComponentFixture<ErrorGroupFilterComponent>;

	beforeEach(async () => {
		await TestBed.configureTestingModule({
			declarations: [
				ErrorGroupsComponent
			],
			imports: [
				ErrorGroupFilterComponent,
				HttpClientTestingModule,
				TranslateModule.forRoot(),
				MatDatepickerModule,
				MatCheckboxModule,
				MatInputModule,
				MatSelectModule,
				MatIconModule,
				MatButtonModule,
				MatAutocompleteModule,
				ObButtonModule,
				ObColumnLayoutModule
			],
			providers: [
				provideNativeDateAdapter(),
				{ provide: DialogService, useValue: {} },
				{ provide: NotifierService, useValue: {} },
				{ provide: ErrorService, useValue: {
						getAllErrorCodes: () => ({ subscribe: fn => fn([]) }),
						getAllEventNames: () => ({ subscribe: fn => fn([]) }),
						getAllEventSources: () => ({ subscribe: fn => fn([]) })
					}}
			]
		}).compileComponents();

		fixture = TestBed.createComponent(ErrorGroupFilterComponent);
		component = fixture.componentInstance;
		fixture.detectChanges();
	});

	it('should create', () => {
		expect(component).toBeTruthy();
	});
});
