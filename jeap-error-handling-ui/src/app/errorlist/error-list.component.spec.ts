import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import {AbstractControl, FormsModule, ReactiveFormsModule, ValidatorFn} from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatSelectModule } from '@angular/material/select';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatIconModule } from '@angular/material/icon';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { FormControl, FormGroup } from '@angular/forms';
import {ErrorListComponent} from './error-list.component';
import {ErrorService} from '../shared/errorservice/error.service';
import {NotifierService} from '../shared/notifier/notifier.service';
import {LogDeepLinkService} from '../shared/logdeeplink/logdeeplink.service';
import {MatPaginatorModule} from '@angular/material/paginator';
import {MatSortModule, Sort} from '@angular/material/sort';
import {MatTableModule} from '@angular/material/table';
import {HttpClientTestingModule} from '@angular/common/http/testing';
import {of} from 'rxjs';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {RouterModule} from '@angular/router';
import {ObliqueTestingModule, ObMockTranslateService} from '@oblique/oblique';
import {MatNativeDateModule} from '@angular/material/core';
import {MatDialog, MatDialogModule} from '@angular/material/dialog';
import {ErrorDTO, ErrorSearchFormDto} from '../shared/errorservice/error.model';

describe('ErrorListComponent', () => {
	let component: ErrorListComponent;
	let fixture: ComponentFixture<ErrorListComponent>;
	let errorService: ErrorService;
	let notifierService: NotifierService;
	let logDeepLinkService: LogDeepLinkService;
	let searchFilterFormGroup: FormGroup;
	let mockData: ErrorDTO[];

	beforeEach(async(() => {
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
				MatAutocompleteModule,
				MatIconModule,
				MatNativeDateModule,
				BrowserAnimationsModule,
				TranslateModule.forRoot(),
				RouterModule,
				ObliqueTestingModule,
			],
			declarations: [ErrorListComponent],
			providers: [
				{provide: TranslateService, useClass: ObMockTranslateService},
				ErrorService,
				NotifierService,
				LogDeepLinkService,
				MatDialog
			]
		})
			.compileComponents();
	}));

	beforeEach(() => {
		fixture = TestBed.createComponent(ErrorListComponent);
		component = fixture.componentInstance;
		errorService = TestBed.get(ErrorService);
		notifierService = TestBed.get(NotifierService);
		logDeepLinkService = TestBed.get(LogDeepLinkService);
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
			closingReason: new FormControl('')
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

	it('should initialize the dropdown values for event sources and error codes', () => {
		const getAllEventSourcesSpy = jest.spyOn(errorService, 'getAllEventSources').mockReturnValue(of([]));
		const getAllErrorCodesSpy = jest.spyOn(errorService, 'getAllErrorCodes').mockReturnValue(of([]));
		component.ngOnInit();
		expect(getAllEventSourcesSpy).toHaveBeenCalled();
		expect(getAllErrorCodesSpy).toHaveBeenCalled();
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
				dateFrom: '2023-01-01',
				dateTo: '2023-01-31',
				eventName: 'EventName',
				traceId: '123456',
				eventId: '7890',
				stacktracePattern: 'Error:.*',
				eventSource: 'EventSource',
				state: 'CLOSED',
				errorCode: '1000',
				sortField: 'timestamp',
				sortOrder: 'desc',
				closingReason: 'Reason'
			});
		});

		it('should set sort direction to desc if direction is empty', () => {
			const sortState: Sort = { active: 'timestamp', direction: '' };
			const result: ErrorSearchFormDto = component.createErrorSearchCriteriaDto(sortState);
			expect(result.sortOrder).toBe('desc');
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
