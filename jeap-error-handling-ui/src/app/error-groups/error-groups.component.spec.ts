import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ErrorGroupsComponent } from './error-groups.component';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';

// Services
import { ErrorGroupService } from '../shared/errorgroupservice/error-group.service';
import { NotifierService } from '../shared/notifier/notifier.service';
import { QdAuthorizationService } from '@quadrel-enterprise-ui/auth';
import {provideTranslateService, TranslatePipe} from '@ngx-translate/core';

@Component({selector: 'app-error-group-filter', template: '', standalone: false})
class ErrorGroupFilterStubComponent {}

@Component({selector: 'ob-column-layout', template: '<ng-content></ng-content>', standalone: false})
class ObColumnLayoutStubComponent {}

describe('ErrorGroupsComponent', () => {
	const sortStorageKey = 'jeap-error-handling.error-groups.sort';
	let component: ErrorGroupsComponent;
	let fixture: ComponentFixture<ErrorGroupsComponent>;
	let errorGroupService: {
		getErrorGroupConfiguration: jest.Mock;
		getGroups: jest.Mock;
	};

	beforeEach(async () => {
		errorGroupService = {
			getErrorGroupConfiguration: jest.fn().mockReturnValue(of({
				ticketingSystemUrl: 'http://mock-url',
				issueTrackingEnabled: true,
				defaultSortField: 'latestErrorAt',
				defaultSortOrder: 'DESC'
			})),
			getGroups: jest.fn().mockReturnValue(of({
				totalErrorGroupCount: 0,
				groups: []
			}))
		};

		await TestBed.configureTestingModule({
			declarations: [ErrorGroupsComponent, ErrorGroupFilterStubComponent, ObColumnLayoutStubComponent],
			imports: [
				HttpClientTestingModule,
				MatPaginatorModule,
				MatSortModule,
				MatTableModule,
				BrowserAnimationsModule,
				RouterTestingModule,
				TranslatePipe,
			],
			providers: [
				provideTranslateService({}),
				{
					provide: ErrorGroupService,
					useValue: errorGroupService
				},
				{
					provide: NotifierService,
					useValue: {
						showFailureNotification: jest.fn()
					}
				},
				{
					provide: QdAuthorizationService,
					useValue: {
						hasRole: jest.fn().mockReturnValue(of(true))
					}
				}
			]
		}).compileComponents();
		localStorage.clear();
	});

	afterEach(() => {
		localStorage.clear();
	});

	function createComponent(): void {
		fixture = TestBed.createComponent(ErrorGroupsComponent);
		component = fixture.componentInstance;
		fixture.detectChanges();
	}

	it('should create', () => {
		createComponent();
		expect(component).toBeTruthy();
	});

	it('should use backend configured default sort when no local preference exists', () => {
		errorGroupService.getErrorGroupConfiguration.mockReturnValue(of({
			ticketingSystemUrl: 'http://mock-url',
			issueTrackingEnabled: true,
			defaultSortField: 'errorCount',
			defaultSortOrder: 'ASC'
		}));

		createComponent();

		expect(component.currentSort).toEqual({active: 'errorCount', direction: 'asc'});
		const searchCriteria = latestSearchCriteria();
		expect(searchCriteria.sortField).toBe('errorCount');
		expect(searchCriteria.sortOrder).toBe('ASC');
	});

	it('should restore persisted sort from localStorage instead of backend configured default', () => {
		localStorage.setItem(sortStorageKey, JSON.stringify({active: 'ticketNumber', direction: 'desc'}));
		errorGroupService.getErrorGroupConfiguration.mockReturnValue(of({
			ticketingSystemUrl: 'http://mock-url',
			issueTrackingEnabled: true,
			defaultSortField: 'errorCount',
			defaultSortOrder: 'ASC'
		}));

		createComponent();

		expect(component.currentSort).toEqual({active: 'ticketNumber', direction: 'desc'});
		const searchCriteria = latestSearchCriteria();
		expect(searchCriteria.sortField).toBe('ticketNumber');
		expect(searchCriteria.sortOrder).toBe('DESC');
	});

	it('should ignore invalid persisted sort and use backend configured default', () => {
		localStorage.setItem(sortStorageKey, JSON.stringify({active: 'unsupportedField', direction: 'desc'}));
		errorGroupService.getErrorGroupConfiguration.mockReturnValue(of({
			ticketingSystemUrl: 'http://mock-url',
			issueTrackingEnabled: true,
			defaultSortField: 'errorCount',
			defaultSortOrder: 'ASC'
		}));

		createComponent();

		expect(component.currentSort).toEqual({active: 'errorCount', direction: 'asc'});
	});

	it('should persist sort changes to localStorage and send updated sort criteria', () => {
		createComponent();

		const sortChange: Sort = {active: 'firstErrorAt', direction: 'asc'};
		component.announceSortChange(sortChange);

		expect(JSON.parse(localStorage.getItem(sortStorageKey) as string)).toEqual(sortChange);
		const searchCriteria = latestSearchCriteria();
		expect(searchCriteria.sortField).toBe('firstErrorAt');
		expect(searchCriteria.sortOrder).toBe('ASC');
	});

	function latestSearchCriteria() {
		const calls = errorGroupService.getGroups.mock.calls;
		return calls[calls.length - 1][2];
	}
});
