import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ErrorGroupDetailsListFilterComponent } from './error-group-details-list-filter.component';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { provideTranslateService } from '@ngx-translate/core';

describe('ErrorGroupDetailsListFilterComponent', () => {
	let component: ErrorGroupDetailsListFilterComponent;
	let fixture: ComponentFixture<ErrorGroupDetailsListFilterComponent>;

	beforeEach(async () => {
		await TestBed.configureTestingModule({
			imports: [
				ErrorGroupDetailsListFilterComponent,
				MatDatepickerModule,
				MatNativeDateModule,
				MatFormFieldModule,
				MatInputModule,
			],
			providers: [
				provideTranslateService({})
			]
		}).compileComponents();

		fixture = TestBed.createComponent(ErrorGroupDetailsListFilterComponent);
		component = fixture.componentInstance;
		fixture.detectChanges();
	});

	it('should create', () => {
		expect(component).toBeTruthy();
	});
});
