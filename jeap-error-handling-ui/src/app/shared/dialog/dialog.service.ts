import { Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import {map} from 'rxjs/operators';
import {ConfirmationDialogComponent} from './confirmation-dialog/confirmation-dialog.component';
import {ClosingReasonDialogComponent} from './closing-reason-dialog/closing-reason-dialog.component';

@Injectable({
	providedIn: 'root'
})
export class DialogService {

	constructor(private dialog: MatDialog) {}

	confirm(message: string): Observable<boolean> {
		const dialogRef = this.dialog.open(ConfirmationDialogComponent, {
			width: '500px',
			data: { message }
		});

		return dialogRef.afterClosed().pipe(
			map(confirmed => !!confirmed)
		);
	}

	getClosingReason(): Observable<string> {
		const dialogRef = this.dialog.open(ClosingReasonDialogComponent, {
			width: '500px',
			data: {}
		});

		return dialogRef.afterClosed().pipe(
			map(reason => reason || null)
		);
	}
}
