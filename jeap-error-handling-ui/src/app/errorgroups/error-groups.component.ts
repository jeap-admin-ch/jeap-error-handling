import {AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {MatPaginator} from "@angular/material/paginator";
import {MatSort} from "@angular/material/sort";
import {ErrorGroupDTO, ErrorGroupResponse} from "../shared/errorgroupservice/error-group.model";
import {MatTableDataSource} from "@angular/material/table";
import {SelectionModel} from "@angular/cdk/collections";
import {Observable, Subscription} from "rxjs";
import {ErrorGroupService} from "../shared/errorgroupservice/error-group.service";
import {environment} from "../../environments/environment";
import {startWith, switchMap} from "rxjs/operators";
import {NotifierService} from "../shared/notifier/notifier.service";
import {Router} from "@angular/router";
import {QdAuthorizationService} from "@quadrel-services/qd-auth";
import {roleFilter_errorgroup_edit} from "../app-routing.module";

@Component({
	selector: 'app-error-groups',
	templateUrl: './error-groups.component.html',
	styleUrl: './error-groups.component.scss'
})
export class ErrorGroupsComponent implements AfterViewInit, OnInit, OnDestroy {
	@ViewChild(MatPaginator, {static: true}) paginator: MatPaginator;
	@ViewChild(MatSort, {static: true}) sort: MatSort;
	@ViewChild('ticketNumberInput') ticketNumberInput: ElementRef;
	@ViewChild('freeTextInput') freeTextInput: ElementRef;
	isLoading = false;
	editingElement: ErrorGroupDTO | null = null;
	isLoadingResults = false;

	displayedColumns: string[];
	data: ErrorGroupDTO[] = [];
	dataSource = new MatTableDataSource<ErrorGroupDTO>([]);
	selection = new SelectionModel<ErrorGroupDTO>(true, []);

	resultsLength = 0;
	protected readonly environment = environment;

	hasEditRoleSubscrition$?: Subscription;
	hasEditRole: boolean = false;

	constructor(private readonly errorGroupService: ErrorGroupService,
				private readonly notifierService: NotifierService,
				private readonly qdAuthorizationService: QdAuthorizationService,
				private readonly router: Router) {
	}

	ngOnInit(): void {
		this.dataSource.sort = this.sort;
		this.dataSource.paginator = this.paginator;
		this.errorGroupService.getTicketNumberLink().subscribe(template => {
			this.environment.TICKETING_SYSTEM_URL = template.ticketingSystemUrl;
		});
		this.setStateDropdownOptions();

		this.hasEditRoleSubscrition$ = this.qdAuthorizationService
			.hasRole(roleFilter_errorgroup_edit)
			.subscribe(value => (this.hasEditRole = value));
	}

	ngOnDestroy(): void {
		this.hasEditRoleSubscrition$?.unsubscribe();
	}

	ngAfterViewInit(): void {
		this.paginator.page.pipe(
			startWith({}),
			switchMap(() => {
				return this.loadGroupErrors(this.paginator.pageIndex);
			})
		).subscribe(
			errorGroupList => this.errorGroupListLoaded(errorGroupList),
			errorMessage => this.notifyFailure(errorMessage));
	}

	setStateDropdownOptions() {
		this.displayedColumns = ['anzahl', 'name', 'quelle', 'meldung', 'fehlerCode', 'erstAuftreten', 'letztesAuftreten', 'freiText', 'jira', 'errorDetails'];
	}

	announceSortChange() {
		this.selection.clear();
		this.loadGroupErrors(this.dataSource.paginator.pageIndex).subscribe(
			errorGroupList => this.errorGroupListLoaded(errorGroupList)
		);
	}


	loadGroupErrors(pageIndex: number): Observable<ErrorGroupResponse> {
		this.isLoadingResults = true;
		const pageSize = this.dataSource.paginator.pageSize;
		return this.errorGroupService.getGroups(pageIndex, pageSize);
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

	announcePaginatorChange() {
		this.selection.clear();
		this.loadGroupErrors(this.dataSource.paginator.pageIndex).subscribe(
			errorList => this.errorGroupListLoaded(errorList)
		);
	}

	startEdit(element: ErrorGroupDTO) {
		this.editingElement = element;
	}

	cancelEdit() {
		this.editingElement = null;
	}

	updateTicketNumber(errorGroupId: string, newTicketNumber: string) {
		this.isLoading = true;
		this.errorGroupService.updateTicketNumber(errorGroupId, newTicketNumber)
			.subscribe((updatedElement: ErrorGroupDTO) => {
				const index = this.data.findIndex(e => e.errorGroupId === updatedElement.errorGroupId);
				if (index !== -1) {
					const updateData = [...this.data];
					updateData[index] = {...updatedElement};
					this.data = updateData;
				}
				this.editingElement = null;
				this.isLoading = false;
			}, error => {
				console.error("Error updating ticket number", error);
				this.notifierService.showFailureNotification(error.error.message,
					'i18n.errorhandling.failure', 'i18n.errorhandling.duplicate.ticket');
				this.isLoading = false;
			});
	}

	updateFreeText(errorGroupId: string, freeText: string) {
		this.isLoading = true;
		this.errorGroupService.updateFreeText(errorGroupId, freeText)
			.subscribe((errorGroup: ErrorGroupDTO) => {
				const index = this.data.findIndex(e => e.errorGroupId === errorGroup.errorGroupId);
				if (index !== -1) {
					const updateData = [...this.data];
					updateData[index] = {...errorGroup};
					this.data = updateData;
				}
				this.editingElement = null;
				this.isLoading = false;
			}, error => {
				console.error("Error updating ticket number", error);
				this.notifierService.showFailureNotification(error.error.message,
					'i18n.errorhandling.failure', 'i18n.errorhandling.duplicate.ticket');
				this.isLoading = false;
			});
	}

	toErrorDetails(ticketNumber: string) {
		this.router.navigate(['/error-list'], {
			queryParams: {ticketNumber},
			queryParamsHandling: 'merge'
		});
	}

	private errorGroupListLoaded(errorGroupResponse: ErrorGroupResponse): void {
		this.isLoadingResults = false;
		this.resultsLength = errorGroupResponse.totalErrorGroupCount;
		this.data = errorGroupResponse.groups;
	}

	private notifyFailure(errorMessage: string): void {
		this.isLoadingResults = false;
		this.resultsLength = 0;
		this.data = [];
		this.notifierService.showFailureNotification(errorMessage,
			'i18n.errorhandling.failure', 'i18n.errorhandling.list.load');
	}
}
