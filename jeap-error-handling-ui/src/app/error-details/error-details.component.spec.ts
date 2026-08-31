import {CommonModule, Location} from '@angular/common';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatButtonModule} from '@angular/material/button';
import {MatSortModule} from '@angular/material/sort';
import {MatTableModule} from '@angular/material/table';
import {ActivatedRoute, convertToParamMap, RouterModule} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {ObNotificationService, ObliqueTestingModule, ObMockTranslateService} from '@oblique/oblique';
import {of} from 'rxjs';
import {DialogService} from '../shared/dialog/dialog.service';
import {ErrorDTO} from '../shared/errorservice/error.model';
import {ErrorService} from '../shared/errorservice/error.service';
import {LogDeepLinkService} from '../shared/logdeeplink/logdeeplink.service';
import {NotifierService} from '../shared/notifier/notifier.service';
import {ErrorDetailsComponent} from './error-details.component';

describe('ErrorDetailsComponent', () => {
	let fixture: ComponentFixture<ErrorDetailsComponent>;
	let errorService: {getErrorDetails: jest.Mock; getCausingEventPayload: jest.Mock; retry: jest.Mock; delete: jest.Mock};
	let dialogService: {confirm: jest.Mock; getClosingReason: jest.Mock};

	const modulithError = {
		id: 'error-id',
		errorState: 'PERMANENT',
		origin: 'MODULITH_PUBLICATION',
		errorTemporality: 'PERMANENT',
		eventClusterName: 'modulith-cluster',
		publicationId: 'publication-id',
		publicationListener: 'example.Listener',
		publicationEventType: 'example.InternalEvent',
		publicationPayloadContentType: 'application/json',
		canRetry: true,
		canDelete: true,
		auditLogDTOs: []
	} as ErrorDTO;

	beforeEach(async () => {
		errorService = {
			getErrorDetails: jest.fn().mockReturnValue(of(modulithError)),
			getCausingEventPayload: jest.fn().mockReturnValue(of('{"value":42}')),
			retry: jest.fn().mockReturnValue(of(undefined)),
			delete: jest.fn().mockReturnValue(of(undefined))
		};
		dialogService = {
			confirm: jest.fn().mockReturnValue(of(true)),
			getClosingReason: jest.fn().mockReturnValue(of('obsolete publication'))
		};

		await TestBed.configureTestingModule({
			imports: [CommonModule, MatButtonModule, MatSortModule, MatTableModule, RouterModule.forRoot([]), ObliqueTestingModule],
			declarations: [ErrorDetailsComponent],
			providers: [
				{provide: ErrorService, useValue: errorService},
				{provide: DialogService, useValue: dialogService},
				{provide: ActivatedRoute, useValue: {paramMap: of(convertToParamMap({errorId: 'error-id'}))}},
				{provide: Location, useValue: {back: jest.fn()}},
				{provide: LogDeepLinkService, useValue: {getLogDeepLink: () => of(''), replaceTraceId: jest.fn()}},
				{provide: NotifierService, useValue: {
					notifySuccess: jest.fn().mockReturnValue(jest.fn()),
					notifyFailure: jest.fn().mockReturnValue(() => of())
				}},
				{provide: TranslateService, useClass: ObMockTranslateService},
				{provide: ObNotificationService, useValue: {success: jest.fn()}}
			]
		}).compileComponents();

		fixture = TestBed.createComponent(ErrorDetailsComponent);
		fixture.detectChanges();
	});

	it('presents Modulith publication details and payload instead of Kafka topic details', () => {
		const element: HTMLElement = fixture.nativeElement;

		expect(element.querySelector('[data-testid="publication-id"]')?.textContent).toContain('publication-id');
		expect(element.querySelector('[data-testid="publication-listener"]')?.textContent).toContain('example.Listener');
		expect(element.querySelector('[data-testid="publication-event-type"]')?.textContent).toContain('example.InternalEvent');
		expect(element.querySelector('[data-testid="publication-content-type"]')?.textContent).toContain('application/json');
		expect(element.querySelector('[data-testid="causing-event-payload"]')?.textContent).toContain('{"value":42}');
		expect(element.querySelector('[data-testid="delete-event-action"]')).toBeNull();
	});

	it('retries a Modulith publication from its dedicated action', () => {
		const button: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="retry-publication-action"]');

		button.click();

		expect(errorService.retry).toHaveBeenCalledWith('error-id');
	});

	it('discards a Modulith publication with the entered reason', () => {
		const button: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="discard-publication-action"]');

		button.click();

		expect(dialogService.getClosingReason).toHaveBeenCalled();
		expect(errorService.delete).toHaveBeenCalledWith('error-id', 'obsolete publication');
	});
});
