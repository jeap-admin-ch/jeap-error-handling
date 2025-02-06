import {ComponentFixture, TestBed} from '@angular/core/testing';
import {ReactivateDeadLetterPageComponent} from './reactivate-dead-letter-page.component';
import {ReactivateDeadLetterService} from '../../shared/reactivate-dead-letter.service';
import {HttpClientTestingModule, HttpTestingController} from '@angular/common/http/testing';
import {TranslateModule} from '@ngx-translate/core';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatInputModule} from '@angular/material/input';
import {ObAlertComponent} from '@oblique/oblique';
import {By} from '@angular/platform-browser';

describe('ReactivateDeadLetterPageComponent', () => {
	let component: ReactivateDeadLetterPageComponent;
	let fixture: ComponentFixture<ReactivateDeadLetterPageComponent>;
	let reactivateDeadLetterService: ReactivateDeadLetterService;
	let httpTestingController: HttpTestingController;

	beforeEach(async () => {
		await TestBed.configureTestingModule({
			imports: [
				HttpClientTestingModule,
				TranslateModule.forRoot(),
				FormsModule,
				MatButtonModule,
				MatInputModule,
				ObAlertComponent,
				ReactivateDeadLetterPageComponent // Add the component here
			],
			providers: [ReactivateDeadLetterService]
		}).compileComponents();

		fixture = TestBed.createComponent(ReactivateDeadLetterPageComponent);
		component = fixture.componentInstance;
		reactivateDeadLetterService = TestBed.inject(ReactivateDeadLetterService);
		httpTestingController = TestBed.inject(HttpTestingController);
		fixture.detectChanges();
	});

	afterEach(() => {
		httpTestingController.verify();
	});

	it('should create', () => {
		expect(component).toBeTruthy();
	});


	it('should set isSuccessfull to true and isInProgress to false on successful reactivation', () => {
		component.reactivateDeadLetter();
		const req = httpTestingController.expectOne('http://localhost:8072/error-handling/api/deadletter/reactivate?maxRecords=1');
		expect(req.request.method).toEqual('POST');
		req.flush({ok: true}, {status: 200, statusText: 'OK'});
		expect(component.isSuccessfull).toBe(true);
		expect(component.isInProgress).toBe(false);
	});

	it('should set status to error status on failed reactivation', () => {
		component.reactivateDeadLetter();
		const req = httpTestingController.expectOne('http://localhost:8072/error-handling/api/deadletter/reactivate?maxRecords=1');
		expect(req.request.method).toEqual('POST');
		req.flush({}, {status: 500, statusText: 'Internal Server Error'});
		expect(component.isSuccessfull).toBe(false);
	});

	it('should display success message when status is 200', () => {
		component.isSuccessfull = true;
		fixture.detectChanges();
		const successAlert = fixture.debugElement.query(By.css('ob-alert[type="success"]'));
		expect(successAlert).toBeTruthy();
	});

	it('should display error message when status is not 200', () => {
		component.isSuccessfull = false;
		fixture.detectChanges();
		const errorAlert = fixture.debugElement.query(By.css('ob-alert[type="error"]'));
		expect(errorAlert).toBeTruthy();
	});
});
