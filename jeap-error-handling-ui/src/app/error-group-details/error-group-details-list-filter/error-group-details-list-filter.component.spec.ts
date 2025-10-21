import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ErrorGroupDetailsListFilterComponent } from './error-group-details-list-filter.component';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';

class FakeTranslateLoader implements TranslateLoader {
	getTranslation(lang: string): Observable<any> {
		return of({});
	}
}

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
				TranslateModule.forRoot({
					loader: { provide: TranslateLoader, useClass: FakeTranslateLoader }
				})
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
