import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ErrorListPageComponent } from './error-list-page.component';
import { MatDialogModule } from '@angular/material/dialog';

@Component({selector: 'error-list', template: '', standalone: false})
class ErrorListStubComponent {}

describe('ErrorListPageComponent', () => {
	let component: ErrorListPageComponent;
	let fixture: ComponentFixture<ErrorListPageComponent>;

	beforeEach(async () => {
		await TestBed.configureTestingModule({
			declarations: [ErrorListPageComponent, ErrorListStubComponent],
			imports: [MatDialogModule]
		}).compileComponents();

		fixture = TestBed.createComponent(ErrorListPageComponent);
		component = fixture.componentInstance;
		fixture.detectChanges();
	});

	it('should create', () => {
		expect(component).toBeTruthy();
	});
});
