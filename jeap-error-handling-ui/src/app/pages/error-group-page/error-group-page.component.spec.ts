import {Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {ErrorGroupPageComponent} from './error-group-page.component';

@Component({selector: 'app-error-groups', template: '', standalone: false})
class ErrorGroupsStubComponent {}

describe('ErrorGroupPageComponent', () => {
	let component: ErrorGroupPageComponent;
	let fixture: ComponentFixture<ErrorGroupPageComponent>;

	beforeEach(async () => {
		await TestBed.configureTestingModule({
			declarations: [ErrorGroupPageComponent, ErrorGroupsStubComponent]
		}).compileComponents();

		fixture = TestBed.createComponent(ErrorGroupPageComponent);
		component = fixture.componentInstance;
		fixture.detectChanges();
	});

	it('should create', () => {
		expect(component).toBeTruthy();
	});
});
