import {AfterViewInit, Component, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {MatPaginator} from '@angular/material/paginator';
import {MatSort, Sort, SortDirection} from '@angular/material/sort';
import {ErrorGroupDTO, ErrorGroupResponse} from '../shared/errorgroupservice/error-group.model';
import {MatTableDataSource} from '@angular/material/table';
import {Observable, Subscription} from 'rxjs';
import {ErrorGroupService} from '../shared/errorgroupservice/error-group.service';
import {environment} from '../../environments/environment';
import {startWith, switchMap} from 'rxjs/operators';
import {NotifierService} from '../shared/notifier/notifier.service';
import {ActivatedRoute, Router} from '@angular/router';
import {QdAuthorizationService} from '@quadrel-enterprise-ui/auth';
import {roleFilter_errorgroup_edit} from '../app-routing.module';
import {ErrorGroupSearchFormDto} from '../shared/errorservice/error.model';
import {ErrorGroupConfiguration} from '../shared/errorgroupservice/error-group-config.model';

const ERROR_GROUP_SORT_STORAGE_KEY = 'jeap-error-handling.error-groups.sort';
const FALLBACK_SORT: Sort = {active: 'latestErrorAt', direction: 'desc'};
const SUPPORTED_SORT_FIELDS = new Set([
	'errorCount',
	'errorEvent',
	'errorPublisher',
	'errorCode',
	'stackTraceHash',
	'firstErrorAt',
	'latestErrorAt',
	'ticketNumber'
]);

@Component({
	selector: 'app-error-groups',
	templateUrl: './error-groups.component.html',
	styleUrls: ['./error-groups.component.scss'],
	standalone: false
})
export class ErrorGroupsComponent implements AfterViewInit, OnInit, OnDestroy {
	@ViewChild(MatPaginator, {static: true}) paginator: MatPaginator;
	@ViewChild(MatSort, {static: true}) sort: MatSort;

	isLoading = false;
	isLoadingResults = false;

	displayedColumns: string[];
	dataSource = new MatTableDataSource<ErrorGroupDTO>([]);

	currentSort: Sort = {...FALLBACK_SORT};
	errorGroupSearchFormDto: ErrorGroupSearchFormDto = this.createErrorGroupSearchFormDto({});

	resultsLength = 0;
	hasEditRoleSubscrition$?: Subscription;
	routeQueryParamsSubscription$?: Subscription;
	paginatorSubscription$?: Subscription;
	hasEditRole: boolean = false;
	protected readonly environment = environment;

	private viewInitialized = false;
	private sortInitialized = false;
	private routeQueryParamsInitialized = false;
	private defaultSort: Sort = {...FALLBACK_SORT};

	constructor(private readonly errorGroupService: ErrorGroupService,
				private readonly notifierService: NotifierService,
				private readonly qdAuthorizationService: QdAuthorizationService,
				private readonly router: Router,
				private readonly route: ActivatedRoute) {
	}

	ngOnInit(): void {

		this.displayedColumns = ['anzahl', 'messageType', 'quelle', 'fehlerCode', 'stackTraceHash', 'erstAuftreten', 'letztesAuftreten', 'jira', 'errorDetails'];

		this.hasEditRoleSubscrition$ = this.qdAuthorizationService
			.hasRole(roleFilter_errorgroup_edit)
			.subscribe(value => (this.hasEditRole = value));

		this.errorGroupService.getErrorGroupConfiguration().subscribe({
			next: config => this.initializeErrorGroupConfiguration(config),
			error: error => {
				console.error('Error loading error group configuration:', error);
				this.initializeSorting();
				this.initializeQueryParamsSubscription();
				this.startPaginatorSubscriptionIfReady();
			}
		});
	}


	ngOnDestroy(): void {
		this.hasEditRoleSubscrition$?.unsubscribe();
		this.routeQueryParamsSubscription$?.unsubscribe();
		this.paginatorSubscription$?.unsubscribe();
	}

	ngAfterViewInit(): void {
		this.dataSource.sort = this.sort;
		this.dataSource.paginator = this.paginator;
		this.applySort(this.currentSort);
		this.viewInitialized = true;
		this.startPaginatorSubscriptionIfReady();
	}

	announceSortChange(sortState: Sort) {
		const normalizedSort = this.normalizeSort(sortState, this.defaultSort) ?? this.defaultSort;
		this.applySort(normalizedSort);
		this.persistSort(normalizedSort);
		this.paginator.pageIndex = 0;
		this.loadGroupErrors(this.paginator.pageIndex, this.errorGroupSearchFormDto).subscribe(
			errorGroupList => this.errorGroupListLoaded(errorGroupList)
		);
	}

	loadGroupErrors(pageIndex: number, errorGroupSearchFormDto: ErrorGroupSearchFormDto): Observable<ErrorGroupResponse> {
		errorGroupSearchFormDto.sortField = this.currentSort.active;
		errorGroupSearchFormDto.sortOrder = this.currentSort.direction.toUpperCase();
		this.isLoadingResults = true;
		const pageSize = this.paginator.pageSize;
		return this.errorGroupService.getGroups(pageIndex, pageSize, errorGroupSearchFormDto);
	}

	announcePaginatorChange() {
		this.loadGroupErrors(this.paginator.pageIndex, this.errorGroupSearchFormDto).subscribe(
			errorList => this.errorGroupListLoaded(errorList)
		);
	}

	onSearch(filterValues: any) {
		this.router.navigate([], {
			queryParams: filterValues,
			queryParamsHandling: 'merge',
		});
	}

	private initializeErrorGroupConfiguration(config: ErrorGroupConfiguration): void {
		this.environment.TICKETING_SYSTEM_URL = config.ticketingSystemUrl;
		this.environment.ISSUE_TRACKING_ENABLED = config.issueTrackingEnabled;
		this.defaultSort = this.sortFromConfiguration(config);
		this.initializeSorting();
		this.initializeQueryParamsSubscription();
		this.startPaginatorSubscriptionIfReady();
	}

	private initializeSorting(): void {
		this.applySort(this.readStoredSort() ?? this.defaultSort);
		this.sortInitialized = true;
	}

	private initializeQueryParamsSubscription(): void {
		if (this.routeQueryParamsSubscription$) {
			return;
		}
		this.routeQueryParamsSubscription$ = this.route.queryParams.subscribe(params => {
			// Use params to set filter values or pass to loadGroupErrors
			this.errorGroupSearchFormDto = this.createErrorGroupSearchFormDto(params);

			// The initial load is triggered by ngAfterViewInit once the paginator is initialized.
			// Here we only react to subsequent query param changes (e.g. filter/search updates).
			if (this.routeQueryParamsInitialized && this.viewInitialized && this.sortInitialized) {
				this.loadGroupErrors(this.paginator.pageIndex, this.errorGroupSearchFormDto).subscribe(
					errorGroupList => this.errorGroupListLoaded(errorGroupList),
					errorMessage => this.notifyFailure(errorMessage)
				);
			}
			this.routeQueryParamsInitialized = true;
		});
	}

	private startPaginatorSubscriptionIfReady(): void {
		if (!this.viewInitialized || !this.sortInitialized || this.paginatorSubscription$) {
			return;
		}
		this.paginatorSubscription$ = this.paginator.page.pipe(
			startWith({}),
			switchMap(() => {
				return this.loadGroupErrors(this.paginator.pageIndex, this.errorGroupSearchFormDto);
			})
		).subscribe(
			errorGroupList => this.errorGroupListLoaded(errorGroupList),
			errorMessage => this.notifyFailure(errorMessage));
	}

	private sortFromConfiguration(config: ErrorGroupConfiguration): Sort {
		return this.normalizeSort({
			active: config.defaultSortField,
			direction: this.toSortDirection(config.defaultSortOrder)
		}, FALLBACK_SORT) ?? {...FALLBACK_SORT};
	}

	private readStoredSort(): Sort | null {
		const storedSort = localStorage.getItem(ERROR_GROUP_SORT_STORAGE_KEY);
		if (!storedSort) {
			return null;
		}
		try {
			return this.normalizeSort(JSON.parse(storedSort), null);
		} catch {
			return null;
		}
	}

	private persistSort(sort: Sort): void {
		localStorage.setItem(ERROR_GROUP_SORT_STORAGE_KEY, JSON.stringify(sort));
	}

	private applySort(sort: Sort): void {
		this.currentSort = {...sort};
		if (this.sort) {
			this.sort.active = sort.active;
			this.sort.direction = sort.direction;
		}
	}

	private normalizeSort(sort: Partial<Sort>, fallback: Sort | null): Sort | null {
		const direction = this.toSortDirection(sort?.direction);
		if (sort?.active && SUPPORTED_SORT_FIELDS.has(sort.active) && direction) {
			return {active: sort.active, direction};
		}
		return fallback ? {...fallback} : null;
	}

	private toSortDirection(sortOrder: string | undefined): SortDirection {
		if (sortOrder?.toLowerCase() === 'asc') {
			return 'asc';
		}
		if (sortOrder?.toLowerCase() === 'desc') {
			return 'desc';
		}
		return '';
	}

	private createErrorGroupSearchFormDto(params: any): ErrorGroupSearchFormDto {
		return {
			noTicket: params['noTicket'] === 'true',
			dateFrom: params['dateFrom'] ?? '',
			dateTo: params['dateTo'] ?? '',
			source: params['source'] ?? '',
			messageType: params['messageType'] ?? '',
			errorCode: params['dropDownErrorCode'] ?? '',
			jiraTicket: params['jiraTicket'] ?? '',
			sortField: this.currentSort.active,
			sortOrder: this.currentSort.direction.toUpperCase()
		};
	}

	private errorGroupListLoaded(errorGroupResponse: ErrorGroupResponse): void {
		this.isLoadingResults = false;
		this.resultsLength = errorGroupResponse.totalErrorGroupCount;
		this.dataSource.data = errorGroupResponse.groups;
	}

	private notifyFailure(errorMessage: string): void {
		this.isLoadingResults = false;
		this.resultsLength = 0;
		this.dataSource.data = [];
		this.notifierService.showFailureNotification(errorMessage,
			'i18n.errorhandling.failure', 'i18n.errorhandling.list.load');
	}
}
