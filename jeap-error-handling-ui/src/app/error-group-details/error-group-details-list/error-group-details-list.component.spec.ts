import {ComponentFixture, TestBed} from '@angular/core/testing';
import {ErrorGroupDetailsListComponent} from './error-group-details-list.component';
import {ErrorService} from '../../shared/errorservice/error.service';
import {NotifierService} from '../../shared/notifier/notifier.service';
import {LogDeepLinkService} from '../../shared/logdeeplink/logdeeplink.service';
import {MatTableModule} from '@angular/material/table';
import {MatCardModule} from '@angular/material/card';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatSortModule} from '@angular/material/sort';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatPaginatorModule} from '@angular/material/paginator';
import {TranslateModule} from '@ngx-translate/core';
import {RouterTestingModule} from '@angular/router/testing';
import {ObButtonModule} from '@oblique/oblique';
import {of} from "rxjs";

describe('ErrorGroupDetailsListComponent', () => {
	let component: ErrorGroupDetailsListComponent;
	let fixture: ComponentFixture<ErrorGroupDetailsListComponent>;

	beforeEach(async () => {
		await TestBed.configureTestingModule({
			imports: [
				ErrorGroupDetailsListComponent,
				MatTableModule,
				MatCardModule,
				MatCheckboxModule,
				MatSortModule,
				MatIconModule,
				MatButtonModule,
				MatTooltipModule,
				MatPaginatorModule,
				TranslateModule.forRoot(),
				RouterTestingModule,
				ObButtonModule
			],
			providers: [
				{ provide: ErrorService, useValue: { someMethod: () => of() } },
				{ provide: NotifierService, useValue: {} },
				{ provide: LogDeepLinkService, useValue: { getLogDeepLink: () => of() } }
			]
		}).compileComponents();

		fixture = TestBed.createComponent(ErrorGroupDetailsListComponent);
		component = fixture.componentInstance;
		fixture.detectChanges();
	});

	it('should create', () => {
		expect(component).toBeTruthy();
	});
});
