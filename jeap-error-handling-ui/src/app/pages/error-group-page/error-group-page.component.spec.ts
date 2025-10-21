import {ComponentFixture, TestBed} from '@angular/core/testing';
import {ErrorGroupPageComponent} from './error-group-page.component';

describe('ErrorGroupPageComponent', () => {
	let component: ErrorGroupPageComponent;
	let fixture: ComponentFixture<ErrorGroupPageComponent>;

	beforeEach(async () => {
		await TestBed.configureTestingModule({
			declarations: [ErrorGroupPageComponent]
			// imports: []  // add needed Angular/material modules here if template requires them
		}).compileComponents();

		fixture = TestBed.createComponent(ErrorGroupPageComponent);
		component = fixture.componentInstance;
		fixture.detectChanges();
	});

	it('should create', () => {
		expect(component).toBeTruthy();
	});
});
