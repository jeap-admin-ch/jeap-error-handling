import {AfterViewInit, ChangeDetectorRef, Component, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {Observable, ReplaySubject, merge, Subject, tap} from 'rxjs';
import {ErrorService} from '../shared/errorservice/error.service';
import {ErrorDTO, ErrorListDTO, ErrorSearchFormDto} from '../shared/errorservice/error.model';
import {ErrorListConfiguration} from '../shared/errorservice/error-list-config.model';
import {NotifierService} from '../shared/notifier/notifier.service';
import {FormControl, FormGroup} from '@angular/forms';
import {MatSort, Sort} from '@angular/material/sort';
import {map, startWith, switchMap, take, takeUntil} from 'rxjs/operators';
import {LogDeepLinkService} from '../shared/logdeeplink/logdeeplink.service';
import {SelectionModel} from '@angular/cdk/collections';
import {TranslateService} from '@ngx-translate/core';
import {ActivatedRoute, Router} from '@angular/router';
import {ErrorSearchFilter} from './error-list.model';
import {endOfDay, startOfDay} from 'date-fns';
import {environment} from '../../environments/environment';
import {DropDownElement} from '../shared/models/drop-down-element.model';
import {BaseComponent} from '../shared/BaseComponent';
import {MatPaginator} from '@angular/material/paginator';

const ERROR_LIST_NO_TICKET_STORAGE_KEY = 'jeap-error-handling.error-list.no-ticket';
const ERROR_LIST_STATE_FILTER_STORAGE_KEY = 'jeap-error-handling.error-list.state-filter';
const ERROR_LIST_SORT_STORAGE_KEY = 'jeap-error-handling.error-list.sort';
const ERROR_LIST_PAGE_SIZE_STORAGE_KEY = 'jeap-error-handling.error-list.page-size';
const DEFAULT_STATE_FILTER = 'PERMANENT';
const FALLBACK_SORT: Sort = {active: 'errorEventMetadata.created', direction: 'desc'};
const SUPPORTED_SORT_FIELDS = new Set([
	'errorEventMetadata.created',
	'causingEvent.metadata.type.name',
	'errorEventMetadata.publisher.service',
	'state',
	'errorEventData.message',
	'errorEventData.code'
]);
const PAGE_SIZE_OPTIONS = [5, 10, 20, 50, 100];
const DEFAULT_PAGE_SIZE = 20;

@Component({
	selector: 'error-list',
	templateUrl: './error-list.component.html',
	styleUrls: ['./error-list.component.css'],
	standalone: false
})
export class ErrorListComponent extends BaseComponent implements AfterViewInit, OnInit, OnDestroy {

	@ViewChild(MatPaginator, {static: false}) paginator: MatPaginator;
	@ViewChild(MatSort, {static: false}) sort: MatSort;

	private destroy$ = new Subject<void>();

	isLoadingResults = false;
	displayedColumns: string[] = ['selection', 'timestamp', 'eventName', 'errorPublisher', 'errorState',
		'nextResend', 'errorMessage', 'errorCode', 'errorDetails'];
	data: ErrorDTO[] = [];
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

	initialSort: Sort = this.readStoredSort() ?? {...FALLBACK_SORT};
	initialPageSize: number = this.readStoredPageSize();

	private defaultNoTicketFilter = false;
	private defaultStateFilter = DEFAULT_STATE_FILTER;
	private filterDefaultsInitialized$ = new ReplaySubject<void>(1);

	protected readonly environment = environment;

	constructor(private readonly errorService: ErrorService,
				private readonly notifierService: NotifierService,
				private readonly logDeepLinkService: LogDeepLinkService,
				protected readonly translateService: TranslateService,
				private readonly router: Router,
				private readonly activatedRoute: ActivatedRoute,
				private readonly cdr: ChangeDetectorRef) {
		super();
	}

	ngOnInit(): void {
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

		this.errorService.getErrorListConfiguration().subscribe({
			next: config => this.initializeFilterDefaults(config),
			error: () => this.initializeFilterDefaults(null)
		});
	}

	/**
	 * Applies the configured filter defaults, then wires up the query param handling. Locally persisted
	 * user settings and query params take precedence over the configured defaults.
	 */
	private initializeFilterDefaults(config: ErrorListConfiguration | null): void {
		if (config) {
			this.defaultNoTicketFilter = config.defaultNoTicketFilter === true;
			this.defaultStateFilter = this.isValidStateFilter(config.defaultStateFilter)
				? config.defaultStateFilter : DEFAULT_STATE_FILTER;
		}
		this.subscribeToQueryParams();
		this.filterDefaultsInitialized$.next();
	}

	private subscribeToQueryParams(): void {
		this.activatedRoute.queryParams
			.pipe(takeUntil(this.destroy$))
			.subscribe((params) => {
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
				// precedence: query param, then locally persisted user setting, then configured default
				this.dropDownStateControl.setValue(
					params.es ?? this.readStoredStateFilter() ?? this.defaultStateFilter, {emitEvent: false});
				this.dropDownErrorCodeControl.setValue(params.ec);
				this.ticketNumberControl.setValue(params.ticketNumber);
				this.noTicketControl.setValue(
					params.noTicket != null ? params.noTicket === 'true' : (this.readStoredNoTicket() ?? this.defaultNoTicketFilter),
					{emitEvent: false});
			});
	}

	ngAfterViewInit(): void {
		// Trigger the initial load once the configured filter defaults are applied,
		// then reload on pagination and sort events
		this.filterDefaultsInitialized$.pipe(
			take(1),
			switchMap(() => merge(
				this.sort.sortChange.pipe(tap(sortState => {
					this.paginator.firstPage();
					this.persistSort(sortState);
				})),
				this.paginator.page.pipe(tap(pageEvent => this.persistPageSize(pageEvent.pageSize)))
			).pipe(startWith({}))),
			tap(() => this.isLoadingResults = true),
			switchMap(() => this.loadErrors(this.paginator.pageIndex, this.sort)),
			takeUntil(this.destroy$)
		).subscribe({
			next: errorList => this.errorListLoaded(errorList),
			error: err => this.notifyFailure(err)
		});

		this.cdr.detectChanges();
	}

	ngOnDestroy(): void {
		this.destroy$.next();
		this.destroy$.complete();
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

		// Update query params
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
		this.errorSearchFilter.noTicket = this.noTicketControl.value === true;

		this.router.navigate([], {
			queryParams: this.errorSearchFilter,
			queryParamsHandling: 'merge',
		});

		// Reset to first page and trigger reload
		if (this.paginator.pageIndex > 0) {
			this.paginator.firstPage();
		} else {
			this.isLoadingResults = true;
			this.loadErrors(0, this.sort)
				.pipe(takeUntil(this.destroy$))
				.subscribe({
					next: (errorList) => this.errorListLoaded(errorList),
					error: (err) => this.notifyFailure(err)
				});
		}
	}

	reset(): void {
		this.resetFormGroup();
		this.data = [];
		this.resultsLength = 0;
		// drop the locally persisted filter settings and return to the configured defaults
		localStorage.removeItem(ERROR_LIST_NO_TICKET_STORAGE_KEY);
		localStorage.removeItem(ERROR_LIST_STATE_FILTER_STORAGE_KEY);
		this.dropDownStateControl.setValue(this.defaultStateFilter, {emitEvent: false});
		this.noTicketControl.setValue(this.defaultNoTicketFilter, {emitEvent: false});
	}

	filterOptions(val: string): string[] {
		const filterValue = val.toLowerCase();
		return this.eventNames.filter(event => event.toLowerCase().includes(filterValue));
	}

	openDeepLink(traceId: string) {
		this.logDeepLink = this.logDeepLinkService.replaceTraceId(this.logDeepLinkTemplate, traceId);
	}

	resendSelected() {
		this.errorService.massRetryWithDialog(
			this.selection.selected,
			() => this.reload(),
			(errorMessage) => this.notifierService.showFailureNotification(
				errorMessage, 'i18n.errorhandling.failure', 'i18n.errorhandling.list.load'
			)
		);
	}

	deleteSelected() {
		this.errorService.massDeleteWithDialog(
			this.selection.selected,
			() => this.reload(),
			(errorMessage) => this.notifierService.showFailureNotification(
				errorMessage, 'i18n.errorhandling.failure', 'i18n.errorhandling.list.load'
			)
		);
	}

	deleteRow(row: ErrorDTO) {
		this.errorService.deleteRowWithDialog(
			row,
			() => this.reload(),
			(error) => this.notifyFailure(error)
		);
	}

	resendRow(row: ErrorDTO) {
		this.errorService.resendRowWithDialog(
			row,
			() => this.reload(),
			(error) => this.notifyFailure(error));
	}

	resetStateSection() {
		this.dropDownStateControl.reset();
		this.closingReasonControl.reset();
	}


	loadErrors(pageIndex: number, sortState: Sort): Observable<ErrorListDTO> {
		const pageSize = this.paginator.pageSize;
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
			ticketNumber: this.ticketNumberControl.value ?? '',
			noTicket: this.noTicketControl.value === true
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
				ticketNumber: new FormControl(''),
				noTicket: new FormControl(false)
			}
		);
		// persist user changes to the filters; programmatic changes are applied with emitEvent: false so
		// that configured defaults do not end up as user settings in the local storage
		this.noTicketControl.valueChanges
			.pipe(takeUntil(this.destroy$))
			.subscribe(value => localStorage.setItem(ERROR_LIST_NO_TICKET_STORAGE_KEY, String(value === true)));
		this.dropDownStateControl.valueChanges
			.pipe(takeUntil(this.destroy$))
			.subscribe(value => {
				if (value) {
					localStorage.setItem(ERROR_LIST_STATE_FILTER_STORAGE_KEY, value);
				} else {
					localStorage.removeItem(ERROR_LIST_STATE_FILTER_STORAGE_KEY);
				}
			});
	}

	private readStoredNoTicket(): boolean | null {
		const storedNoTicket = localStorage.getItem(ERROR_LIST_NO_TICKET_STORAGE_KEY);
		return storedNoTicket == null ? null : storedNoTicket === 'true';
	}

	private readStoredStateFilter(): string | null {
		const storedStateFilter = localStorage.getItem(ERROR_LIST_STATE_FILTER_STORAGE_KEY);
		return storedStateFilter && this.isValidStateFilter(storedStateFilter) ? storedStateFilter : null;
	}

	private isValidStateFilter(value: string): boolean {
		return this.dropDownState.some(state => state.value === value);
	}

	private readStoredSort(): Sort | null {
		const storedSort = localStorage.getItem(ERROR_LIST_SORT_STORAGE_KEY);
		if (!storedSort) {
			return null;
		}
		try {
			return this.normalizeSort(JSON.parse(storedSort));
		} catch {
			return null;
		}
	}

	private normalizeSort(sort: Partial<Sort>): Sort | null {
		const direction = sort?.direction === 'asc' || sort?.direction === 'desc' ? sort.direction : null;
		if (sort?.active && SUPPORTED_SORT_FIELDS.has(sort.active) && direction) {
			return {active: sort.active, direction};
		}
		return null;
	}

	private persistSort(sort: Sort): void {
		localStorage.setItem(ERROR_LIST_SORT_STORAGE_KEY, JSON.stringify({active: sort.active, direction: sort.direction}));
	}

	private readStoredPageSize(): number {
		const storedPageSize = Number(localStorage.getItem(ERROR_LIST_PAGE_SIZE_STORAGE_KEY));
		return PAGE_SIZE_OPTIONS.includes(storedPageSize) ? storedPageSize : DEFAULT_PAGE_SIZE;
	}

	private persistPageSize(pageSize: number): void {
		localStorage.setItem(ERROR_LIST_PAGE_SIZE_STORAGE_KEY, String(pageSize));
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

	get noTicketControl(): FormControl {
		return this.searchFilterFormGroup.get('noTicket');
	}
}
