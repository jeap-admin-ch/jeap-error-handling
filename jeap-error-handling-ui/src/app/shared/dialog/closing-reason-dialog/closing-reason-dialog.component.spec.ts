import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { ClosingReasonDialogComponent } from './closing-reason-dialog.component';
import { TranslateModule } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import {CUSTOM_ELEMENTS_SCHEMA} from '@angular/core';

describe('ClosingReasonDialogComponent', () => {
	let component: ClosingReasonDialogComponent;
	let fixture: ComponentFixture<ClosingReasonDialogComponent>;
	let dialogRef: MatDialogRef<ClosingReasonDialogComponent>;
	const matDialogData: any = { inputText: '' };

	beforeEach(async () => {
		await TestBed.configureTestingModule({
			imports: [
				TranslateModule.forRoot(),
				FormsModule,
				MatFormFieldModule,
				MatInputModule,
				MatButtonModule,
				BrowserAnimationsModule
			],
			declarations: [ClosingReasonDialogComponent],
			providers: [
				{
					provide: MatDialogRef,
					useValue: {
						close: jest.fn(),
					},
				},
				{ provide: MAT_DIALOG_DATA, useValue: matDialogData },
			],
			schemas: [CUSTOM_ELEMENTS_SCHEMA]
		}).compileComponents();
	});

	beforeEach(() => {
		fixture = TestBed.createComponent(ClosingReasonDialogComponent);
		component = fixture.componentInstance;
		dialogRef = TestBed.inject(MatDialogRef);
		fixture.detectChanges();
	});

	it('should create the component', () => {
		expect(component).toBeTruthy();
	});
});
