import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { ConfirmationDialogComponent } from './confirmation-dialog.component';

describe('ConfirmationDialogComponent', () => {
	let component: ConfirmationDialogComponent;
	let fixture: ComponentFixture<ConfirmationDialogComponent>;
	let matDialogRef: MatDialogRef<ConfirmationDialogComponent>;

	beforeEach(async () => {
		await TestBed.configureTestingModule({
			declarations: [ ConfirmationDialogComponent ],
			imports: [ BrowserAnimationsModule, TranslateModule.forRoot() ],
			providers: [
				{
					provide: MatDialogRef,
					useValue: { close: jest.fn() },
				},
				{
					provide: MAT_DIALOG_DATA,
					useValue: {
						message: 'Are you sure?',
					},
				},
			]
		})
			.compileComponents();
	});

	beforeEach(() => {
		fixture = TestBed.createComponent(ConfirmationDialogComponent);
		component = fixture.componentInstance;
		matDialogRef = TestBed.inject(MatDialogRef);
		fixture.detectChanges();
	});

	it('should create the component', () => {
		expect(component).toBeTruthy();
	});

	it('should display the message passed in through the MAT_DIALOG_DATA', () => {
		const dialogContentElement: HTMLElement = fixture.nativeElement.querySelector('mat-dialog-content');
		expect(dialogContentElement.textContent).toContain('Are you sure?');
	});


	it('should close the dialog with true when the submit button is clicked', () => {
		const buttons: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('button[mat-button]');
		const submitButton: HTMLElement = buttons[0];
		submitButton.click();

		fixture.whenStable().then(() => {
			expect(matDialogRef.close).toHaveBeenCalledWith(true);
		});
	});


	it('should close the dialog with false when the cancel button is clicked', () => {
		const buttons: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('button[mat-button]');
		const cancelButton: HTMLElement = buttons[1];
		cancelButton.click();
		fixture.whenStable().then(() => {
			expect(matDialogRef.close).toHaveBeenCalledWith(false);
		});
	});
});
