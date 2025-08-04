import {AfterViewInit, ChangeDetectorRef, Component, OnInit, ViewChild} from '@angular/core';
import {Observable} from 'rxjs';
import {ErrorService} from '../shared/errorservice/error.service';
import {ErrorDTO, ErrorListDTO, ErrorSearchFormDto} from '../shared/errorservice/error.model';
import {NotifierService} from '../shared/notifier/notifier.service';
import {AbstractControl, FormControl, FormGroup, ValidatorFn} from '@angular/forms';
import {MatSort, Sort} from '@angular/material/sort';
import {MatPaginator, PageEvent} from '@angular/material/paginator';
import {MatTableDataSource} from '@angular/material/table';
import {map, startWith, switchMap} from 'rxjs/operators';
import {LogDeepLinkService} from '../shared/logdeeplink/logdeeplink.service';
import {SelectionModel} from '@angular/cdk/collections';
import {TranslateService} from '@ngx-translate/core';
import {DialogService} from '../shared/dialog/dialog.service';
import {ActivatedRoute, Router} from '@angular/router';
import {ErrorSearchFilter} from './error-list.model';
import {endOfDay, startOfDay} from 'date-fns';
import {environment} from '../../environments/environment';

interface DropDownElement {
	value: string;
	viewValue: string;
}

@Component({
	selector: 'error-list',
	templateUrl: './error-list.component.html',
	styleUrls: ['./error-list.component.css']
})
export class ErrorListComponent implements AfterViewInit, OnInit {

	@ViewChild(MatPaginator, {static: true}) paginator: MatPaginator;
	@ViewChild(MatSort, {static: true}) sort: MatSort;

	isLoadingResults = false;
	displayedColumns: string[] = ['selection', 'timestamp', 'eventName', 'errorPublisher', 'errorState',
		'nextResend', 'errorMessage', 'errorCode', 'errorDetails'];
	data: ErrorDTO[] = [];
	dataSource = new MatTableDataSource<ErrorDTO>([]);
	selection = new SelectionModel<ErrorDTO>(true, []);

	resultsLength = 0;
	searchFilterFormGroup;
	dropDownEventSources: DropDownElement[] = [];
	dropDownErrorCodes: DropDownElement[] = [];
	dropDownState: DropDownElement[] = [
		{value: 'PERMANENT', viewValue: 'i18n.error.errorstate.PERMANENT'},
		{value: 'TEMPORARY', viewValue: 'i18n.error.errorstate.TEMPORARY'},
		{value: 'RETRIED', viewValue: 'i18n.error.errorstate.RETRIED'},
		{value: 'DELETED', viewValue: 'i18n.error.errorstate.DELETED'}
	];

	eventNames = [];
	filteredEventNames: Observable<string[]>;
	logDeepLink: string;
	logDeepLinkTemplate: string;

	errorSearchFilter = new ErrorSearchFilter();

	protected readonly environment = environment;

	constructor(private readonly errorService: ErrorService,
				private readonly notifierService: NotifierService,
				private readonly logDeepLinkService: LogDeepLinkService,
				protected readonly translateService: TranslateService,
				private readonly dialogService: DialogService,
				private readonly router: Router,
				private readonly activatedRoute: ActivatedRoute,
				private readonly cdr: ChangeDetectorRef) {
	}

	ngOnInit(): void {
		this.dataSource.sort = this.sort;
		this.dataSource.paginator = this.paginator;
		this.errorService.getAllEventSources().subscribe(eventSources => {
			eventSources.forEach(eventSource => {
				const element: DropDownElement = {value: eventSource.valueOf(), viewValue: eventSource.valueOf()};
				this.dropDownEventSources.push(element);
			});
		});

		this.errorService.getAllErrorCodes().subscribe(errorCodes => {
			errorCodes.forEach(errorCode => {
				const element: DropDownElement = {value: errorCode.valueOf(), viewValue: errorCode.valueOf()};
				this.dropDownErrorCodes.push(element);
			});
		});

		this.errorService.getAllEventNames().subscribe(eventNames => {
			this.eventNames = eventNames;
		});

		this.logDeepLinkService.getLogDeepLink().subscribe(template => {
			this.logDeepLinkTemplate = template;
		});

		this.resetFormGroup();

		this.filteredEventNames = this.eventNameControl.valueChanges.pipe(
			startWith(''),
			map(value => this.filterOptions(value || '')),
		);

		this.activatedRoute.queryParams.subscribe((params) => {
			if (params.from) {
				this.datePickerFromControl.setValue(new Date(params.from));
			}
			if (params.to) {
				this.datePickerToControl.setValue(new Date(params.to));
			}
			this.eventNameControl.setValue(params.en);
			this.traceIdControl.setValue(params.traceId);
			this.eventIdControl.setValue(params.eventId);
			this.stacktraceControl.setValue(params.st);
			this.dropDownEventSourceControl.setValue(params.source);
			this.dropDownStateControl.setValue(params.es);
			this.dropDownErrorCodeControl.setValue(params.ec);
			this.ticketNumberControl.setValue(params.ticketNumber);
		});

		this.dropDownStateControl.setValue('PERMANENT');
	}

	ngAfterViewInit(): void {
		this.paginator.page.pipe(
			startWith({}),
			switchMap(() => {
				return this.loadErrors(this.paginator.pageIndex, this.sort);
			})
		).subscribe(
			errorList => this.errorListLoaded(errorList),
			errorMessage => this.notifyFailure(errorMessage));
		this.cdr.detectChanges();
	}

	isAllSelected() {
		const numSelected = this.selection.selected.length;
		const numRows = this.data.length;
		return numSelected === numRows;
	}

	masterToggle() {
		this.isAllSelected() ?
			this.selection.clear() :
			this.data.forEach(row => this.selection.select(row));
	}

	isActionDisabled(action: 'Delete' | 'Retry') {
		return this.selection.selected.length === 0 ||
			this.selection.selected.some(e => e['can' + action] === false);
	}

	hasInput() {
		return Object.values(this.searchFilterFormGroup.value).some(val => val);
	}

	reload(): void {
		this.selection.clear();
		if (this.paginator.pageIndex > 0) {
			this.paginator.firstPage();
		} else {
			this.loadErrors(0, this.sort).subscribe(
				errorList => this.errorListLoaded(errorList),
				errorMessage => this.notifyFailure(errorMessage));
		}

		this.errorSearchFilter.from = this.retrieveDateValue(this.datePickerFromControl, startOfDay);
		this.errorSearchFilter.to = this.retrieveDateValue(this.datePickerToControl, endOfDay);
		this.errorSearchFilter.en = this.retrieveValue(this.eventNameControl);
		this.errorSearchFilter.traceId = this.retrieveValue(this.traceIdControl);
		this.errorSearchFilter.eventId = this.retrieveValue(this.eventIdControl);
		this.errorSearchFilter.st = this.retrieveValue(this.stacktraceControl);
		this.errorSearchFilter.source = this.retrieveValue(this.dropDownEventSourceControl);
		this.errorSearchFilter.es = this.retrieveValue(this.dropDownStateControl);
		this.errorSearchFilter.ec = this.retrieveValue(this.dropDownErrorCodeControl);
		this.errorSearchFilter.ticketNumber = this.retrieveValue(this.ticketNumberControl);

		this.router.navigate([], {
			queryParams: this.errorSearchFilter,
			queryParamsHandling: 'merge',
		});
	}

	reset(): void {
		this.resetFormGroup();
		this.data = [];
		this.dropDownStateControl.setValue('PERMANENT');
	}

	filterOptions(val: string): string[] {
		const filterValue = val.toLowerCase();
		return this.eventNames.filter(event => event.toLowerCase().includes(filterValue));
	}

	announcePaginatorChange(event: PageEvent) {
		this.selection.clear();
		this.loadErrors(this.dataSource.paginator.pageIndex, this.sort).subscribe(
			errorList => this.errorListLoaded(errorList)
		);
	}

	announceSortChange(sortState: Sort) {
		this.selection.clear();
		this.loadErrors(this.dataSource.paginator.pageIndex, sortState).subscribe(
			errorList => this.errorListLoaded(errorList)
		);
	}

	openDeepLink(traceId: string) {
		this.logDeepLink = this.logDeepLinkService.replaceTraceId(this.logDeepLinkTemplate, traceId);
	}

	resendSelected() {
		const message = this.selection.selected.some(e => e.errorState.includes('DELETED'))
			? this.translateService.instant('i18n.errorhandling.confirm.closed-errors')
			: this.translateService.instant('i18n.errorhandling.confirm', {
				count: this.selection.selected.length
			});

		this.dialogService.confirm(message).subscribe(confirmed => {
			if (confirmed) {
				const errorIds: string[] = this.selection.selected.map(error => error.id);
				this.errorService.massRetry(errorIds).subscribe(
					() => {
						this.reload();
						this.notifierService.notifySuccess('i18n.errorhandling.action.retry', 'i18n.errorhandling.action.success')();
					},
					(error) => {
						this.notifyFailure(error);
					}
				);
			}
		});
	}

	deleteSelected() {
		const count = this.selection.selected.length;
		const message = this.translateService.instant('i18n.errorhandling.confirm', {count});

		this.dialogService.confirm(message).subscribe(confirmed => {
			if (confirmed) {
				this.dialogService.getClosingReason().subscribe(reason => {
					if (reason != null) {
						const errorIds: string[] = this.selection.selected.map(error => error.id);
						this.errorService.massDelete(errorIds, reason).subscribe(
							() => {
								this.reload();
								this.notifierService.notifySuccess('i18n.errorhandling.action.delete', 'i18n.errorhandling.action.success')();
							},
							(error) => {
								this.notifyFailure(error);
							}
						);
					}
				});
			}
		});
	}

	deleteRow(row: ErrorDTO) {
		this.dialogService.getClosingReason().subscribe(reason => {
			if (reason != null) {
				this.errorService.delete(row.id, reason).subscribe(
					() => {
						this.reload();
						this.notifierService.notifySuccess('i18n.errorhandling.action.delete', 'i18n.errorhandling.action.success')();
					},
					(error) => {
						this.notifyFailure(error);
					}
				);
			}

		});
	}

	resendRow(row: ErrorDTO) {
		if (row.errorState.includes('DELETED')) {
			this.dialogService.confirm(
				this.translateService.instant('i18n.errorhandling.confirm.closed-error')).subscribe(confirmed => {
				if (confirmed) {
					this.resendEntry(row.id);
				}
			});
		} else {
			this.resendEntry(row.id);
		}
	}

	resendEntry(id: string) {
		this.errorService.retry(id).subscribe(
			() => {
				this.reload();
				this.notifierService.notifySuccess('i18n.errorhandling.action.retry', 'i18n.errorhandling.action.success')();
			},
			(error) => {
				this.notifyFailure(error);
			}
		);
	}

	resetStateSection() {
		this.dropDownStateControl.reset();
		this.closingReasonControl.reset();
	}

	regexValidator(): ValidatorFn {
		return (control: AbstractControl): { [key: string]: any } | null => {
			const regexString = control.value;
			if (!regexString) {
				return null;
			}
			try {
				// tslint:disable-next-line:no-unused-expression
				new RegExp(regexString);
				return null;
			} catch (e) {
				return {'invalidRegex': {value: regexString}};
			}
		};
	}

	loadErrors(pageIndex: number, sortState: Sort): Observable<ErrorListDTO> {
		this.isLoadingResults = true;
		const pageSize = this.dataSource.paginator.pageSize;
		return this.errorService.findErrorsByFilter(pageIndex, pageSize, this.createErrorSearchCriteriaDto(sortState));
	}

	createErrorSearchCriteriaDto(sortState: Sort): ErrorSearchFormDto {
		if (sortState.direction === '') {
			sortState.direction = 'desc';
		}
		return {
			dateFrom: this.retrieveDateValue(this.datePickerFromControl, startOfDay) ?? '',
			dateTo: this.retrieveDateValue(this.datePickerToControl, endOfDay) ?? '',
			eventName: this.eventNameControl.value ?? '',
			traceId: this.traceIdControl.value ?? '',
			eventId: this.eventIdControl.value ?? '',
			stacktracePattern: this.stacktraceControl.value ?? '',
			eventSource: this.dropDownEventSourceControl.value,
			states: this.dropDownStateControl.value ? this.retrieveStates(this.dropDownStateControl.value) : null,
			errorCode: this.dropDownErrorCodeControl.value,
			sortField: sortState.active,
			sortOrder: sortState.direction,
			closingReason: this.closingReasonControl.value ?? '',
			ticketNumber: this.ticketNumberControl.value ?? ''
		};
	}

	traceIdOrEventIdChanged(event: any) {
		if (event.target.value) {
			this.dropDownStateControl.reset();
		}
	}

	private retrieveDateValue = (formControl: FormControl, dateTransformer: (date: Date) => Date): string =>
		formControl.value ? dateTransformer(new Date(formControl.value)).toISOString() : null

	private retrieveValue(formControl: FormControl): string {
		if (formControl.value) {
			return formControl.value;
		}
		return null;
	}

	private retrieveStates(state: string): string[] {
		switch (state) {
			case 'PERMANENT': {
				return ['PERMANENT', 'SEND_TO_MANUALTASK'];
			}
			case 'TEMPORARY': {
				return ['TEMPORARY_RETRY_PENDING'];
			}
			case 'RETRIED': {
				return ['TEMPORARY_RETRIED', 'PERMANENT_RETRIED', 'RESOLVE_ON_MANUALTASK'];
			}
			case 'DELETED': {
				return ['DELETE_ON_MANUALTASK', 'DELETED'];
			}
			default: {
				return null;
			}
		}
	}

	private errorListLoaded(errorList: ErrorListDTO): void {
		this.isLoadingResults = false;
		this.resultsLength = errorList.totalErrorCount;
		this.data = errorList.errors;
	}

	private notifyFailure(errorMessage: string): void {
		this.isLoadingResults = false;
		this.resultsLength = 0;
		this.data = [];
		this.notifierService.showFailureNotification(errorMessage,
			'i18n.errorhandling.failure', 'i18n.errorhandling.list.load');
	}


	private resetFormGroup() {
		this.searchFilterFormGroup = new FormGroup({
				datePickerFrom: new FormControl(''),
				datePickerTo: new FormControl(''),
				eventName: new FormControl(''),
				traceId: new FormControl(''),
				eventId: new FormControl(''),
				stacktrace: new FormControl('', this.regexValidator()),
				dropDownEventSource: new FormControl(),
				dropDownState: new FormControl(),
				dropDownErrorCode: new FormControl(),
				closingReason: new FormControl(''),
				ticketNumber: new FormControl('')
			}
		);
	}

	get datePickerFromControl(): FormControl {
		return this.searchFilterFormGroup.get('datePickerFrom');
	}

	get datePickerToControl(): FormControl {
		return this.searchFilterFormGroup.get('datePickerTo');
	}

	get eventNameControl(): FormControl {
		return this.searchFilterFormGroup.get('eventName');
	}

	get traceIdControl(): FormControl {
		return this.searchFilterFormGroup.get('traceId');
	}

	get eventIdControl(): FormControl {
		return this.searchFilterFormGroup.get('eventId');
	}

	get stacktraceControl(): FormControl {
		return this.searchFilterFormGroup.get('stacktrace');
	}

	get dropDownEventSourceControl(): FormControl {
		return this.searchFilterFormGroup.get('dropDownEventSource');
	}

	get dropDownStateControl(): FormControl {
		return this.searchFilterFormGroup.get('dropDownState');
	}

	get dropDownErrorCodeControl(): FormControl {
		return this.searchFilterFormGroup.get('dropDownErrorCode');
	}

	get closingReasonControl(): FormControl {
		return this.searchFilterFormGroup.get('closingReason');
	}

	get ticketNumberControl(): FormControl {
		return this.searchFilterFormGroup.get('ticketNumber');
	}
}
