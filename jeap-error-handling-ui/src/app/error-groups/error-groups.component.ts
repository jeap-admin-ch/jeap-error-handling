import {AfterViewInit, Component, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {MatPaginator} from "@angular/material/paginator";
import {MatSort, Sort} from "@angular/material/sort";
import {ErrorGroupDTO, ErrorGroupResponse} from "../shared/errorgroupservice/error-group.model";
import {MatTableDataSource} from "@angular/material/table";
import {Observable, Subscription} from "rxjs";
import {ErrorGroupService} from "../shared/errorgroupservice/error-group.service";
import {environment} from "../../environments/environment";
import {startWith, switchMap} from "rxjs/operators";
import {NotifierService} from "../shared/notifier/notifier.service";
import {ActivatedRoute, Router} from "@angular/router";
import {QdAuthorizationService} from "@quadrel-services/qd-auth";
import {roleFilter_errorgroup_edit} from "../app-routing.module";
import {ErrorGroupSearchFormDto} from "../shared/errorservice/error.model";

@Component({
	selector: 'app-error-groups',
	templateUrl: './error-groups.component.html',
	styleUrl: './error-groups.component.scss'
})
export class ErrorGroupsComponent implements AfterViewInit, OnInit, OnDestroy {
	@ViewChild(MatPaginator, {static: true}) paginator: MatPaginator;
	@ViewChild(MatSort, {static: true}) sort: MatSort;

	isLoading = false;
	isLoadingResults = false;

	displayedColumns: string[];
	dataSource = new MatTableDataSource<ErrorGroupDTO>([]);

	errorGroupSearchFormDto: ErrorGroupSearchFormDto;

	resultsLength = 0;
	protected readonly environment = environment;

	hasEditRoleSubscrition$?: Subscription;
	hasEditRole: boolean = false;

	constructor(private readonly errorGroupService: ErrorGroupService,
				private readonly notifierService: NotifierService,
				private readonly qdAuthorizationService: QdAuthorizationService,
				private readonly router: Router,
				private readonly route: ActivatedRoute) {
	}

	ngOnInit(): void {

		this.errorGroupService.getTicketNumberLink().subscribe(template => {
			this.environment.TICKETING_SYSTEM_URL = template.ticketingSystemUrl;
		});
		this.displayedColumns = ['anzahl', 'messageType', 'quelle', 'fehlerCode', 'stackTraceHash', 'erstAuftreten', 'letztesAuftreten', 'jira', 'errorDetails'];

		this.hasEditRoleSubscrition$ = this.qdAuthorizationService
			.hasRole(roleFilter_errorgroup_edit)
			.subscribe(value => (this.hasEditRole = value));

		this.route.queryParams.subscribe(params => {
			// Use params to set filter values or pass to loadGroupErrors
			this.errorGroupSearchFormDto = {
				noTicket: params['noTicket'] === 'true',
				dateFrom: params['dateFrom'] ?? '',
				dateTo: params['dateTo'] ?? '',
				source: params['source'] ?? '',
				messageType: params['messageType'] ?? '',
				errorCode: params['dropDownErrorCode'] ?? '',
				jiraTicket: params['jiraTicket'] ?? '',
				sortField: this.sort.active ?? 'latestErrorAt',
				sortOrder: this.sort.direction?.toUpperCase() ?? 'DESC'
			}

			const pageIndex = this.paginator.pageIndex;
			this.loadGroupErrors(pageIndex, this.errorGroupSearchFormDto).subscribe(
				errorGroupList => this.errorGroupListLoaded(errorGroupList),
				errorMessage => this.notifyFailure(errorMessage)
			);
		});
	}


	ngOnDestroy(): void {
		this.hasEditRoleSubscrition$?.unsubscribe();
	}

	ngAfterViewInit(): void {
		this.dataSource.sort = this.sort;
		this.dataSource.paginator = this.paginator;
		this.paginator.page.pipe(
			startWith({}),
			switchMap(() => {
				return this.loadGroupErrors(this.paginator.pageIndex, this.errorGroupSearchFormDto);
			})
		).subscribe(
			errorGroupList => this.errorGroupListLoaded(errorGroupList),
			errorMessage => this.notifyFailure(errorMessage));
	}

	announceSortChange(sortState: Sort) {
		this.loadGroupErrors(this.dataSource.paginator.pageIndex, this.errorGroupSearchFormDto).subscribe(
			errorGroupList => this.errorGroupListLoaded(errorGroupList)
		);
	}

	loadGroupErrors(pageIndex: number, errorGroupSearchFormDto: ErrorGroupSearchFormDto): Observable<ErrorGroupResponse> {
		errorGroupSearchFormDto.sortField = this.sort.active ?? 'latestErrorAt';
		errorGroupSearchFormDto.sortOrder = this.sort.direction?.toUpperCase() ?? 'DESC';
		console.log("Loading Error Groups with: ", pageIndex, this.dataSource.paginator.pageSize, errorGroupSearchFormDto);
		this.isLoadingResults = true;
		const pageSize = this.dataSource.paginator.pageSize;
		return this.errorGroupService.getGroups(pageIndex, pageSize, errorGroupSearchFormDto);
	}

	announcePaginatorChange() {
		this.loadGroupErrors(this.dataSource.paginator.pageIndex, this.errorGroupSearchFormDto).subscribe(
			errorList => this.errorGroupListLoaded(errorList)
		);
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

	onSearch(filterValues: any) {
		this.router.navigate([], {
			queryParams: filterValues,
			queryParamsHandling: 'merge',
		 });
	}
}
