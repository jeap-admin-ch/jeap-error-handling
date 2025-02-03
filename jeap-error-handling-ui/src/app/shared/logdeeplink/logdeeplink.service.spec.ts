import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { LogDeepLinkService } from './logdeeplink.service';

describe('LogDeepLinkService', () => {
	let service: LogDeepLinkService;
	let httpTestingController: HttpTestingController;

	beforeEach(() => {
		TestBed.configureTestingModule({
			imports: [HttpClientTestingModule],
			providers: [LogDeepLinkService]
		});
		service = TestBed.inject(LogDeepLinkService);
		httpTestingController = TestBed.inject(HttpTestingController);
	});

	afterEach(() => {
		httpTestingController.verify();
	});

	describe('getLogDeepLink', () => {
		it('should make a GET request to the correct URL and return the response', () => {
			const expectedResponse = 'This is a mock';
			service.getLogDeepLink().subscribe(response => {
				expect(response).toEqual(expectedResponse);
			});
			const request = httpTestingController.expectOne(`${environment.BACKEND_SERVICE_API}/configuration/log-deeplink`);
			expect(request.request.method).toBe('GET');
			request.flush(expectedResponse);
		});
	});

	describe('replaceTraceId', () => {
		it('should replace instances of "{traceId}" in the encoded URL with the trace value', () => {
			const encodedUrl = 'http://localhost:8080/log-deeplink?traceId=%7BtraceId%7D';
			const traceId = '1234567890';
			const expectedResponse = 'http://localhost:8080/log-deeplink?traceId=1234567890';
			const response = service.replaceTraceId(encodedUrl, traceId);
			expect(response).toEqual(expectedResponse);
		});
	});
});
