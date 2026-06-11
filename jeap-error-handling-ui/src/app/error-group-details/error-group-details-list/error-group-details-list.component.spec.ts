import {ComponentFixture, TestBed} from '@angular/core/testing';
import {ErrorGroupDetailsListComponent} from './error-group-details-list.component';
import {ErrorService} from '../../shared/errorservice/error.service';
import {NotifierService} from '../../shared/notifier/notifier.service';
import {LogDeepLinkService} from '../../shared/logdeeplink/logdeeplink.service';
import {MatTableModule} from '@angular/material/table';
import {MatCardModule} from '@angular/material/card';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatSortModule} from '@angular/material/sort';
import {MatIconModule, MatIconRegistry} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatPaginatorModule} from '@angular/material/paginator';
import {TranslateModule} from '@ngx-translate/core';
import {RouterTestingModule} from '@angular/router/testing';
import {ObButtonModule} from '@oblique/oblique';
import {DomSanitizer} from '@angular/platform-browser';
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

		// Oblique registers these SVG icons at runtime; register them here so mat-icon can resolve them in tests.
		const iconRegistry = TestBed.inject(MatIconRegistry);
		const sanitizer = TestBed.inject(DomSanitizer);
		['arrow_clockwise', 'delete', 'eye', 'link_external', 'xmark_circle'].forEach(name =>
			iconRegistry.addSvgIconLiteral(name, sanitizer.bypassSecurityTrustHtml('<svg></svg>'))
		);

		fixture = TestBed.createComponent(ErrorGroupDetailsListComponent);
		component = fixture.componentInstance;
		fixture.detectChanges();
	});

	it('should create', () => {
		expect(component).toBeTruthy();
	});
});
