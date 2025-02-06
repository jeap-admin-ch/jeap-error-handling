import { Component } from '@angular/core';
import { ReactivateDeadLetterService } from '../../shared/reactivate-dead-letter.service';
import {FormsModule} from '@angular/forms';
import {MatButton} from '@angular/material/button';
import {TranslateModule} from '@ngx-translate/core';
import {MatInput} from '@angular/material/input';
import {ObAlertComponent} from '@oblique/oblique';
import {NgIf} from '@angular/common';
import {MatProgressBar} from "@angular/material/progress-bar";
import {MatIcon} from "@angular/material/icon";
@Component({
	selector: 'app-reactivate-dead-letter-page',
	templateUrl: './reactivate-dead-letter-page.component.html',
	standalone: true,
	imports: [
		FormsModule,
		MatButton,
		TranslateModule,
		MatInput,
		ObAlertComponent,
		NgIf,
		MatProgressBar,
		MatIcon
	],
	styleUrls: ['./reactivate-dead-letter-page.component.scss']
})
export class ReactivateDeadLetterPageComponent {

	maxMessages = 1;
	isSuccessfull: boolean | null = null;
	isInProgress = false;

	constructor(
		private readonly reactivateDeadLetterService: ReactivateDeadLetterService
	) {
	}

	reactivateDeadLetter() {
		this.isInProgress = true;
		this.reactivateDeadLetterService.reactivateDeadLetter(this.maxMessages).subscribe({
			next: response => {
				this.isSuccessfull = response.status === 200;
				this.isInProgress = false;
			},
			error: error => {
				console.error('Error occurred:', error);
				this.isSuccessfull = false;
				this.isInProgress = false;
			}
		});
	}
}
