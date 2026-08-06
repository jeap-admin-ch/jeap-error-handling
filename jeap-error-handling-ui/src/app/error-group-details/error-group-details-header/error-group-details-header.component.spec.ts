// TypeScript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ErrorGroupDetailsHeaderComponent } from './error-group-details-header.component';
import { provideHttpClient } from '@angular/common/http';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { ErrorGroupService } from '../../shared/errorgroupservice/error-group.service';
import { ObNotificationService } from '@oblique/oblique';

class FakeTranslateLoader implements TranslateLoader {
	getTranslation(lang: string): Observable<any> {
		return of({});
	}
}

describe('ErrorGroupDetailsHeaderComponent', () => {
	let fixture: ComponentFixture<ErrorGroupDetailsHeaderComponent>;
	let component: ErrorGroupDetailsHeaderComponent;

	beforeEach(async () => {
		await TestBed.configureTestingModule({
			imports: [
				ErrorGroupDetailsHeaderComponent,
				TranslateModule.forRoot({
					loader: { provide: TranslateLoader, useClass: FakeTranslateLoader }
				})
			],
			providers: [
				provideHttpClient(),
				{
					provide: ErrorGroupService,
					useValue: {
						createIssue: () => of({ errorGroupId: '1', ticketNumber: 'TEST-123' }),
						updateFreeText: () => of({ errorGroupId: '1', ticketNumber: 'TEST-123' }),
						updateTicketNumber: () => of({ errorGroupId: '1', ticketNumber: 'UPDATED-123' })
					}
				},
				{
					provide: ObNotificationService,
					useValue: {
						success: () => {},
						error: () => {}
					}
				}
			]
		}).compileComponents();

		fixture = TestBed.createComponent(ErrorGroupDetailsHeaderComponent);

		// Correct input name: errorGroupDetails
		fixture.componentRef.setInput('errorGroupDetails', {
			errorGroupId: '1',
			ticketNumber: 'TEST-123'
		});

		component = fixture.componentInstance;
		fixture.detectChanges();
	});

	it('should create', () => {
		expect(component).toBeTruthy();
	});
});
