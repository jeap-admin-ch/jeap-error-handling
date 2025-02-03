import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';

@Component({
	selector: 'app-closing-reason-dialog',
	templateUrl: './closing-reason-dialog.component.html',
	styleUrls: ['./closing-reason-dialog.component.scss']
})
export class ClosingReasonDialogComponent {

	constructor(public dialogRef: MatDialogRef<ClosingReasonDialogComponent>,
				@Inject(MAT_DIALOG_DATA) public data: any) { }

	onCancelClick(): void {
		this.dialogRef.close();
	}
}
