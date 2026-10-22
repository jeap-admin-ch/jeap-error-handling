import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { QdAuthenticationService } from '@quadrel-enterprise-ui/auth';
import { BehaviorSubject } from 'rxjs';
import { DeadLetterCountService } from './dead-letter-count.service';

describe('DeadLetterCountService', () => {
	let service: DeadLetterCountService;
	let httpTestingController: HttpTestingController;
	let authenticated$: BehaviorSubject<boolean>;
	const url = `${environment.BACKEND_SERVICE_API}/deadletter/count`;
	const updateFrequencyUrl = `${environment.BACKEND_SERVICE_API}/configuration/dead-letter-count-update-frequency`;

	beforeEach(() => {
		authenticated$ = new BehaviorSubject<boolean>(true);
		TestBed.configureTestingModule({
			imports: [HttpClientTestingModule],
			providers: [
				DeadLetterCountService,
				{ provide: QdAuthenticationService, useValue: { isAuthenticated$: authenticated$.asObservable() } }
			]
		});
		service = TestBed.inject(DeadLetterCountService);
		httpTestingController = TestBed.inject(HttpTestingController);

		// the constructor also fetches the configured poll interval; flush it here so it does not
		// interfere with the count-focused assertions below
		httpTestingController.expectOne(updateFrequencyUrl).flush('30000');
	});

	afterEach(() => {
		httpTestingController.verify();
	});

	it('should fetch the pending message count immediately on construction', () => {
		let emitted: number | null = null;
		service.count$.subscribe(count => (emitted = count));

		httpTestingController.expectOne(url).flush({ pendingMessageCount: 5 });

		expect(emitted).toBe(5);
	});

	it('should refresh the pending message count on demand', () => {
		httpTestingController.expectOne(url).flush({ pendingMessageCount: 0 });

		let emitted: number | null = null;
		service.count$.subscribe(count => (emitted = count));
		service.refresh();

		httpTestingController.expectOne(url).flush({ pendingMessageCount: 3 });

		expect(emitted).toBe(3);
	});

	it('should emit null when the request fails', () => {
		httpTestingController.expectOne(url).flush({ pendingMessageCount: 0 });

		let emitted: number | null = null;
		service.count$.subscribe(count => (emitted = count));
		service.refresh();

		httpTestingController.expectOne(url).flush({}, { status: 500, statusText: 'Internal Server Error' });

		expect(emitted).toBeNull();
	});

	it('should emit null for the backend\'s "not yet determined" sentinel instead of showing it as zero', () => {
		httpTestingController.expectOne(url).flush({ pendingMessageCount: 0 });

		let emitted: number | null = null;
		service.count$.subscribe(count => (emitted = count));
		service.refresh();

		httpTestingController.expectOne(url).flush({ pendingMessageCount: -1 });

		expect(emitted).toBeNull();
	});
});

describe('DeadLetterCountService authentication', () => {
	const url = `${environment.BACKEND_SERVICE_API}/deadletter/count`;
	const updateFrequencyUrl = `${environment.BACKEND_SERVICE_API}/configuration/dead-letter-count-update-frequency`;

	it('should not request anything while the user is not authenticated, and start once they are', () => {
		const notAuthenticated$ = new BehaviorSubject<boolean>(false);
		TestBed.configureTestingModule({
			imports: [HttpClientTestingModule],
			providers: [
				DeadLetterCountService,
				{ provide: QdAuthenticationService, useValue: { isAuthenticated$: notAuthenticated$.asObservable() } }
			]
		});
		TestBed.inject(DeadLetterCountService);
		const http = TestBed.inject(HttpTestingController);

		http.expectNone(url);
		http.expectNone(updateFrequencyUrl);

		notAuthenticated$.next(true);

		http.expectOne(url).flush({ pendingMessageCount: 0 });
		http.expectOne(updateFrequencyUrl).flush('30000');
		http.verify();
	});

	it('should ignore refresh() while the user is not authenticated', () => {
		const notAuthenticated$ = new BehaviorSubject<boolean>(false);
		TestBed.configureTestingModule({
			imports: [HttpClientTestingModule],
			providers: [
				DeadLetterCountService,
				{ provide: QdAuthenticationService, useValue: { isAuthenticated$: notAuthenticated$.asObservable() } }
			]
		});
		const service = TestBed.inject(DeadLetterCountService);
		const http = TestBed.inject(HttpTestingController);

		service.refresh();

		http.expectNone(url);
		http.verify();
	});
});
