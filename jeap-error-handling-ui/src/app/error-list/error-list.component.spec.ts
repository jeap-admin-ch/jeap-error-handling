import {ComponentFixture, TestBed, waitForAsync} from '@angular/core/testing';
import {AbstractControl, FormControl, FormGroup, FormsModule, ReactiveFormsModule, ValidatorFn} from '@angular/forms';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatSelectModule} from '@angular/material/select';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatAutocompleteModule} from '@angular/material/autocomplete';
import {MatIconModule} from '@angular/material/icon';
import {BrowserAnimationsModule, NoopAnimationsModule} from '@angular/platform-browser/animations';
import {ErrorListComponent} from './error-list.component';
import {ErrorService} from '../shared/errorservice/error.service';
import {NotifierService} from '../shared/notifier/notifier.service';
import {LogDeepLinkService} from '../shared/logdeeplink/logdeeplink.service';
import {MatPaginatorModule} from '@angular/material/paginator';
import {MatSortModule, Sort} from '@angular/material/sort';
import {MatTableModule} from '@angular/material/table';
import {HttpClientTestingModule} from '@angular/common/http/testing';
import {TranslateService} from '@ngx-translate/core';
import {ActivatedRoute, RouterModule} from '@angular/router';
import {ObliqueTestingModule, ObMockTranslateService} from '@oblique/oblique';
import {MatNativeDateModule} from '@angular/material/core';
import {MatDialog, MatDialogModule} from '@angular/material/dialog';
import {ErrorDTO, ErrorSearchFormDto} from '../shared/errorservice/error.model';
import {endOfDay, startOfDay} from 'date-fns';
import {of} from 'rxjs';

describe('ErrorListComponent', () => {
	let component: ErrorListComponent;
	let fixture: ComponentFixture<ErrorListComponent>;
	let errorService: ErrorService;
	let notifierService: NotifierService;
	let logDeepLinkService: LogDeepLinkService;
	let searchFilterFormGroup: FormGroup;
	let mockData: ErrorDTO[];

	beforeEach(waitForAsync(() => {
		TestBed.configureTestingModule({
			imports: [
				MatDialogModule,
				MatPaginatorModule,
				MatSortModule,
				MatTableModule,
				NoopAnimationsModule,
				HttpClientTestingModule,
				FormsModule,
				ReactiveFormsModule,
				MatFormFieldModule,
				MatInputModule,
				MatDatepickerModule,
				MatSelectModule,
				MatCheckboxModule,
				MatAutocompleteModule,
				MatIconModule,
				MatNativeDateModule,
				BrowserAnimationsModule,

				RouterModule,
				ObliqueTestingModule,
			],
			declarations: [ErrorListComponent],
			providers: [
				{ provide: TranslateService, useClass: ObMockTranslateService },
				{ provide: ActivatedRoute, useValue: { snapshot: {}, queryParams: of({}) } },
				ErrorService,
				NotifierService,
				LogDeepLinkService,
				MatDialog
			]
		})
			.compileComponents();
	}));

	beforeEach(() => {
		errorService = TestBed.inject(ErrorService);
		jest.spyOn(errorService, 'getErrorListConfiguration').mockReturnValue(
			of({defaultNoTicketFilter: false, defaultStateFilter: 'PERMANENT'}));
		fixture = TestBed.createComponent(ErrorListComponent);
		component = fixture.componentInstance;
		notifierService = TestBed.inject(NotifierService);
		logDeepLinkService = TestBed.inject(LogDeepLinkService);
		searchFilterFormGroup = new FormGroup({
				datePickerFrom: new FormControl(''),
				datePickerTo: new FormControl(''),
				eventName: new FormControl(''),
				traceId: new FormControl(''),
				eventId: new FormControl(''),
				stacktrace: new FormControl(''),
				dropDownEventSource: new FormControl(),
				dropDownState: new FormControl(),
				dropDownErrorCode: new FormControl(),
				closingReason: new FormControl(''),
				ticketNumber: new FormControl(''),
				noTicket: new FormControl(false)
			}
		);
		component.searchFilterFormGroup = searchFilterFormGroup;
		component.eventNames = ['Event 1', 'Event 2', 'Event 3'];
		mockData = [{ id: '1', eventName: 'Event 1' } as ErrorDTO, { id: '2', eventName: 'Event 2' } as ErrorDTO, { id: '3', eventName: 'Event 3' } as ErrorDTO];
		component.data = mockData;
	});

	it('should create the component', () => {
		expect(component).toBeTruthy();
	});

	it('should return true if at least one form control has a value', () => {
		searchFilterFormGroup.get('eventName').setValue('Event 1');
		expect(component.hasInput()).toBe(true);
	});

	it('should return false if no form control has a value', () => {
		expect(component.hasInput()).toBe(false);
	});

	it('should filter options based on the input value', () => {
		const filteredOptions = component.filterOptions('Event 2');
		expect(filteredOptions).toEqual(['Event 2']);
	});

	it('should return all options if the input value is empty', () => {
		const filteredOptions = component.filterOptions('');
		expect(filteredOptions).toEqual(['Event 1', 'Event 2', 'Event 3']);
	});

	it('should reset the state section', () => {
		const dropDownStateControlSpy = jest.spyOn(component.searchFilterFormGroup.get('dropDownState'), 'reset');
		const closingReasonControlSpy = jest.spyOn(component.searchFilterFormGroup.get('closingReason'), 'reset');
		component.resetStateSection();
		expect(dropDownStateControlSpy).toHaveBeenCalled();
		expect(closingReasonControlSpy).toHaveBeenCalled();
	});


	describe('regexValidator', () => {
		it('should return null for a valid regex', () => {
			const regexValidator: ValidatorFn = component.regexValidator();
			const control: AbstractControl = new FormControl('^[a-z]+$');
			const result = regexValidator(control);
			expect(result).toBeNull();
		});

		it('should return an error for an invalid regex', () => {
			const regexValidator: ValidatorFn = component.regexValidator();
			const control: AbstractControl = new FormControl('*');
			const result = regexValidator(control);
			expect(result).toEqual({ 'invalidRegex': { value: '*' } });
		});
	});

	describe('isAllSelected', () => {
		it('should return true if all rows are selected', () => {
			component.selection.select(...mockData);
			expect(component.isAllSelected()).toBe(true);
		});

		it('should return false if not all rows are selected', () => {
			component.selection.select(mockData[0]);
			expect(component.isAllSelected()).toBe(false);
		});

		it('should return false if no rows are selected', () => {
			expect(component.isAllSelected()).toBe(false);
		});
	});

	describe('masterToggle', () => {
		it('should select all rows if none are currently selected', () => {
			component.masterToggle();
			expect(component.selection.selected).toEqual(mockData);
		});

		it('should clear all selected rows if all are currently selected', () => {
			component.selection.select(...mockData);
			component.masterToggle();
			expect(component.selection.selected).toEqual([]);
		});

		it('should toggle the selection of all rows if some are currently selected', () => {
			component.selection.select(mockData[0], mockData[1]);
			component.masterToggle();
			expect(component.selection.selected).toEqual(mockData);
			component.masterToggle();
			expect(component.selection.selected).toEqual([]);
		});
	});

	describe('createErrorSearchCriteriaDto', () => {
		it('should return ErrorSearchFormDto object with valid data', () => {
			const sortState: Sort = { active: 'timestamp', direction: 'desc' };
			component.datePickerFromControl.setValue('2023-01-01');
			component.datePickerToControl.setValue('2023-01-31');
			component.eventNameControl.setValue('EventName');
			component.traceIdControl.setValue('123456');
			component.eventIdControl.setValue('7890');
			component.stacktraceControl.setValue('Error:.*');
			component.dropDownEventSourceControl.setValue('EventSource');
			component.dropDownStateControl.setValue('CLOSED');
			component.dropDownErrorCodeControl.setValue('1000');
			component.closingReasonControl.setValue('Reason');

			const result: ErrorSearchFormDto = component.createErrorSearchCriteriaDto(sortState);

			expect(result).toEqual({
				dateFrom: startOfDay(new Date('2023-01-01')).toISOString(),
				dateTo: endOfDay(new Date('2023-01-31')).toISOString(),
				eventName: 'EventName',
				traceId: '123456',
				eventId: '7890',
				stacktracePattern: 'Error:.*',
				eventSource: 'EventSource',
				errorCode: '1000',
				sortField: 'timestamp',
				sortOrder: 'desc',
				closingReason: 'Reason',
				states: null,
				ticketNumber: '',
				noTicket: false
			});
		});

		it('should set sort direction to desc if direction is empty', () => {
			const sortState: Sort = { active: 'timestamp', direction: '' };
			const result: ErrorSearchFormDto = component.createErrorSearchCriteriaDto(sortState);
			expect(result.sortOrder).toBe('desc');
		});
	});

	describe('localStorage persistence', () => {
		beforeEach(() => {
			localStorage.clear();
		});

		afterEach(() => {
			localStorage.clear();
		});

		it('should restore persisted sort and page size from localStorage', () => {
			localStorage.setItem('jeap-error-handling.error-list.sort', JSON.stringify({active: 'errorEventData.code', direction: 'asc'}));
			localStorage.setItem('jeap-error-handling.error-list.page-size', '50');

			const localFixture = TestBed.createComponent(ErrorListComponent);

			expect(localFixture.componentInstance.initialSort).toEqual({active: 'errorEventData.code', direction: 'asc'});
			expect(localFixture.componentInstance.initialPageSize).toBe(50);
		});

		it('should fall back to defaults for unsupported stored sort and page size', () => {
			localStorage.setItem('jeap-error-handling.error-list.sort', JSON.stringify({active: 'unsupportedField', direction: 'asc'}));
			localStorage.setItem('jeap-error-handling.error-list.page-size', '42');

			const localFixture = TestBed.createComponent(ErrorListComponent);

			expect(localFixture.componentInstance.initialSort).toEqual({active: 'errorEventMetadata.created', direction: 'desc'});
			expect(localFixture.componentInstance.initialPageSize).toBe(20);
		});

		it('should restore the noTicket filter from localStorage', () => {
			localStorage.setItem('jeap-error-handling.error-list.no-ticket', 'true');

			const localFixture = TestBed.createComponent(ErrorListComponent);
			localFixture.componentInstance.ngOnInit();

			expect(localFixture.componentInstance.noTicketControl.value).toBe(true);
		});

		it('should persist noTicket changes to localStorage', () => {
			const localFixture = TestBed.createComponent(ErrorListComponent);
			localFixture.componentInstance.ngOnInit();

			localFixture.componentInstance.noTicketControl.setValue(true);

			expect(localStorage.getItem('jeap-error-handling.error-list.no-ticket')).toBe('true');
		});
	});

	describe('configurable filter defaults', () => {
		beforeEach(() => {
			localStorage.clear();
		});

		afterEach(() => {
			localStorage.clear();
		});

		function createComponentWithConfig(config: any): ErrorListComponent {
			(errorService.getErrorListConfiguration as jest.Mock).mockReturnValue(of(config));
			const localFixture = TestBed.createComponent(ErrorListComponent);
			localFixture.componentInstance.ngOnInit();
			return localFixture.componentInstance;
		}

		it('should apply the configured defaults when nothing is stored locally', () => {
			const localComponent = createComponentWithConfig({defaultNoTicketFilter: true, defaultStateFilter: 'TEMPORARY'});

			expect(localComponent.noTicketControl.value).toBe(true);
			expect(localComponent.dropDownStateControl.value).toBe('TEMPORARY');
			// applying configured defaults must not persist them as user settings
			expect(localStorage.getItem('jeap-error-handling.error-list.no-ticket')).toBeNull();
			expect(localStorage.getItem('jeap-error-handling.error-list.state-filter')).toBeNull();
		});

		it('should prefer locally stored user settings over the configured defaults', () => {
			localStorage.setItem('jeap-error-handling.error-list.no-ticket', 'false');
			localStorage.setItem('jeap-error-handling.error-list.state-filter', 'DELETED');

			const localComponent = createComponentWithConfig({defaultNoTicketFilter: true, defaultStateFilter: 'TEMPORARY'});

			expect(localComponent.noTicketControl.value).toBe(false);
			expect(localComponent.dropDownStateControl.value).toBe('DELETED');
		});

		it('should fall back to PERMANENT for an unsupported configured state filter', () => {
			const localComponent = createComponentWithConfig({defaultNoTicketFilter: false, defaultStateFilter: 'BOGUS'});

			expect(localComponent.dropDownStateControl.value).toBe('PERMANENT');
		});

		it('should persist user changes of the state filter and clear the setting when the filter is reset', () => {
			const localComponent = createComponentWithConfig({defaultNoTicketFilter: false, defaultStateFilter: 'PERMANENT'});

			localComponent.dropDownStateControl.setValue('DELETED');
			expect(localStorage.getItem('jeap-error-handling.error-list.state-filter')).toBe('DELETED');

			localComponent.dropDownStateControl.reset();
			expect(localStorage.getItem('jeap-error-handling.error-list.state-filter')).toBeNull();
		});

		it('should return to the configured defaults on reset and drop the stored user settings', () => {
			const localComponent = createComponentWithConfig({defaultNoTicketFilter: true, defaultStateFilter: 'TEMPORARY'});

			localComponent.dropDownStateControl.setValue('DELETED');
			localComponent.noTicketControl.setValue(false);
			localComponent.reset();

			expect(localComponent.dropDownStateControl.value).toBe('TEMPORARY');
			expect(localComponent.noTicketControl.value).toBe(true);
			expect(localStorage.getItem('jeap-error-handling.error-list.no-ticket')).toBeNull();
			expect(localStorage.getItem('jeap-error-handling.error-list.state-filter')).toBeNull();
		});
	});

	describe('isActionDisabled', () => {

		it('should return true when selection is empty', () => {
			expect(component.isActionDisabled('Delete')).toBe(true);
			expect(component.isActionDisabled('Retry')).toBe(true);
		});

		it('should return true when some selected elements cannot perform the action', () => {
			mockData[0].canDelete = true;
			mockData[0].canRetry = false;
			mockData[1].canDelete = false;
			mockData[1].canRetry = true;
			mockData[2].canDelete = false;
			mockData[2].canRetry = true;
			component.selection.select(mockData[0], mockData[1], mockData[2]);
			expect(component.isActionDisabled('Delete')).toBe(true);
			expect(component.isActionDisabled('Retry')).toBe(true);
		});

		it('should return false when all selected elements can perform the action', () => {
			mockData[0].canDelete = true;
			mockData[0].canRetry = true;
			mockData[1].canDelete = true;
			mockData[1].canRetry = true;
			mockData[2].canDelete = true;
			mockData[2].canRetry = true;
			component.selection.select(mockData[0], mockData[1], mockData[2]);
			expect(component.isActionDisabled('Delete')).toBe(false);
			expect(component.isActionDisabled('Retry')).toBe(false);
		});
	});
});
