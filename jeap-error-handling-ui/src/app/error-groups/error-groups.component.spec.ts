import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ErrorGroupsComponent } from './error-groups.component';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSortModule } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';

// Services
import { ErrorGroupService } from '../shared/errorgroupservice/error-group.service';
import { NotifierService } from '../shared/notifier/notifier.service';
import { QdAuthorizationService } from '@quadrel-services/qd-auth';
import {TranslateModule} from '@ngx-translate/core';

describe('ErrorGroupsComponent', () => {
	let component: ErrorGroupsComponent;
	let fixture: ComponentFixture<ErrorGroupsComponent>;

	beforeEach(async () => {
		await TestBed.configureTestingModule({
			declarations: [ErrorGroupsComponent],
			imports: [
				HttpClientTestingModule,
				MatPaginatorModule,
				MatSortModule,
				MatTableModule,
				BrowserAnimationsModule,
				RouterTestingModule,
				TranslateModule.forRoot()
			],
			providers: [
				{
					provide: ErrorGroupService,
					useValue: {
						getErrorGroupConfiguration: jest.fn().mockReturnValue(of({
							ticketingSystemUrl: 'http://mock-url',
							issueTrackingEnabled: true
						})),
						getGroups: jest.fn().mockReturnValue(of({
							totalErrorGroupCount: 0,
							groups: []
						}))
					}
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

		fixture = TestBed.createComponent(ErrorGroupsComponent);
		component = fixture.componentInstance;
		fixture.detectChanges();
	});

	it('should create', () => {
		expect(component).toBeTruthy();
	});
});
