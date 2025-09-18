import {
	AfterViewInit,
	ChangeDetectorRef,
	Component,
	inject,
	Input,
	OnChanges,
	OnInit,
	SimpleChanges,
	ViewChild
} from '@angular/core';
import {ErrorService} from "../../shared/errorservice/error.service";
import {ObButtonDirective, ObCheckboxDirective, ObPaginatorModule} from "@oblique/oblique";
import {ErrorDTO, ErrorGroupDetailsListSearchFormDto, ErrorListDTO} from "../../shared/errorservice/error.model";
import {NgIf} from "@angular/common";
import {MatTableDataSource, MatTableModule} from '@angular/material/table';
import {MatCard, MatCardContent} from "@angular/material/card";
import {MatCheckbox} from "@angular/material/checkbox";
import {SelectionModel} from "@angular/cdk/collections";
import {MatSort, MatSortHeader, Sort} from "@angular/material/sort";
import {MatIcon} from "@angular/material/icon";
import {MatIconAnchor, MatIconButton} from "@angular/material/button";
import {MatTooltip} from "@angular/material/tooltip";
import {TranslateModule} from "@ngx-translate/core";
import {RouterLink} from "@angular/router";
import {NotifierService} from "../../shared/notifier/notifier.service";
import {MatPaginator, PageEvent} from "@angular/material/paginator";
import {Observable} from "rxjs";
import {startWith, switchMap} from "rxjs/operators";
import {LogDeepLinkService} from "../../shared/logdeeplink/logdeeplink.service";


@Component({
	selector: 'app-error-group-details-list',
	standalone: true,
	imports: [
		MatTableModule,
		MatCard,
		MatCardContent,
		MatCheckbox,
		ObCheckboxDirective,
		MatSort,
		MatIcon,
		MatIconAnchor,
		MatIconButton,
		MatTooltip,
		NgIf,
		ObButtonDirective,
		TranslateModule,
		RouterLink,
		MatPaginator,
		ObPaginatorModule,
		MatSortHeader
	],
	templateUrl: './error-group-details-list.component.html',
	styleUrl: './error-group-details-list.component.scss'
})
export class ErrorGroupDetailsListComponent implements OnInit, AfterViewInit, OnChanges {

	private readonly errorService = inject(ErrorService);
	private readonly notifierService = inject(NotifierService);
	private readonly logDeepLinkService = inject(LogDeepLinkService);
	private readonly cdr = inject(ChangeDetectorRef);

	@ViewChild(MatPaginator, {static: true}) paginator: MatPaginator;
	@ViewChild(MatSort, {static: true}) sort: MatSort;

	@Input() errorGroupId: string;
	@Input() searchCriteria: ErrorGroupDetailsListSearchFormDto;

	isLoadingResults = false;
	resultsLength = 0;

	displayedColumns: string[] = ['selection', 'timestamp', 'errorMessage', 'errorDetails'];

	dataSource = new MatTableDataSource<ErrorDTO>([]);
	selection = new SelectionModel<ErrorDTO>(true, []);

	logDeepLink: string;
	logDeepLinkTemplate: string;

	ngOnInit(): void {
		this.logDeepLinkService.getLogDeepLink().subscribe(template => {
			this.logDeepLinkTemplate = template;
		});
	}

	ngAfterViewInit(): void {
		console.log("### Error Group ID:", this.errorGroupId);
		console.log("### Search Criteria:", this.searchCriteria);
		this.dataSource.sort = this.sort;
		this.dataSource.paginator = this.paginator;
		this.paginator.page.pipe(
			startWith({}),
			switchMap(() => {
				return this.loadErrors(this.errorGroupId, this.searchCriteria, this.paginator.pageIndex, this.sort);
			})
		).subscribe({
			next: errorList => this.errorListLoaded(errorList),
			error: errorMessage => this.notifyFailure(errorMessage)
		});
		this.cdr.detectChanges();
	}

	ngOnChanges(changes: SimpleChanges): void {
		console.log("### Changes detected in ErrorGroupDetailsListComponent:", changes);
		if (changes['searchCriteria'] && !changes['searchCriteria'].firstChange) {
			this.reload(); // Reload data when search criteria changes
		}
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

	private errorListLoaded(errorList: ErrorListDTO): void {
		this.isLoadingResults = false;
		this.resultsLength = errorList.totalErrorCount;
		this.dataSource.data = errorList.errors;
	}

	private notifyFailure(errorMessage: string): void {
		this.isLoadingResults = false;
		this.resultsLength = 0;
		this.dataSource.data = [];
		this.notifierService.showFailureNotification(errorMessage,
			'i18n.errorhandling.failure', 'i18n.errorhandling.list.load');
	}

	loadErrors(errorGroupId: string,
			   errorGroupDetailsListSearchFormDto: ErrorGroupDetailsListSearchFormDto,
			   pageIndex: number,
			   sortState: Sort): Observable<ErrorListDTO> {

		this.isLoadingResults = true;

		const pageSize = this.dataSource.paginator.pageSize;
		return this.errorService.findErrorsByErrorGroupIdAndCriteria(errorGroupId, errorGroupDetailsListSearchFormDto, pageIndex, pageSize, sortState);
	}

	reload(): void {
		this.selection.clear();
		if (this.paginator.pageIndex > 0) {
			this.paginator.firstPage();
		} else {
			this.loadErrors(this.errorGroupId, this.searchCriteria, 0, this.sort).subscribe({
				next: errorList => this.errorListLoaded(errorList),
				error: errorMessage => this.notifyFailure(errorMessage)
			});
		}
	}

	openDeepLink(traceId: string) {
		this.logDeepLink = this.logDeepLinkService.replaceTraceId(this.logDeepLinkTemplate, traceId);
	}

	announcePaginatorChange(event: PageEvent) {
		this.selection.clear();
		this.loadErrors(this.errorGroupId, this.searchCriteria, this.dataSource.paginator.pageIndex, this.sort).subscribe({
			next: errorList => this.errorListLoaded(errorList)
		});
	}

	announceSortChange(sortState: Sort) {
		console.log("Sort changed:", sortState);
		this.loadErrors(this.errorGroupId, this.searchCriteria, this.dataSource.paginator.pageIndex, sortState).subscribe({
			next: errorList => this.errorListLoaded(errorList)
		});
	}

	masterToggle() {
		this.isAllSelected() ?
			this.selection.clear() :
			this.dataSource.data.forEach(row => this.selection.select(row));
	}

	isAllSelected() {
		const numSelected = this.selection.selected.length;
		const numRows = this.dataSource.data.length;
		return numSelected === numRows;
	}

	isActionDisabled(action: 'Delete' | 'Retry') {

		return this.selection.selected.length === 0 ||
			this.selection.selected.some(e => e['can' + action] === false);
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

	resendSelected() {
		this.errorService.massRetryWithDialog(
			this.selection.selected,
			() => this.reload(),
			(errorMessage) => this.notifierService.showFailureNotification(
				errorMessage, 'i18n.errorhandling.failure', 'i18n.errorhandling.list.load'
			)
		);
	}
}
