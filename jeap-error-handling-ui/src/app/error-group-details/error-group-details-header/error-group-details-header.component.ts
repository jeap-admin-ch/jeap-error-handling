import {Component, inject, Input} from '@angular/core';
import {ErrorGroupDTO} from '../../shared/errorgroupservice/error-group.model';
import {ObButtonDirective, ObNotificationService} from '@oblique/oblique';
import {MatFormFieldModule, MatLabel} from '@angular/material/form-field';
import {MatInput} from '@angular/material/input';
import {MatButton} from '@angular/material/button';
import {MatCard, MatCardContent, MatCardHeader} from '@angular/material/card';
import {TranslateModule} from '@ngx-translate/core';
import {FormsModule} from '@angular/forms';
import {NgIf} from '@angular/common';
import {ErrorGroupService} from '../../shared/errorgroupservice/error-group.service';
import {environment} from '../../../environments/environment';

@Component({
	selector: 'app-error-group-details-header',
	standalone: true,
	imports: [
		MatFormFieldModule,
		MatLabel,
		MatInput,
		MatButton,
		MatCard,
		MatCardContent,
		MatCardHeader,
		ObButtonDirective,
		TranslateModule,
		FormsModule,
		NgIf
	],
	templateUrl: './error-group-details-header.component.html',
	styleUrls: ['./error-group-details-header.component.scss']
})
export class ErrorGroupDetailsHeaderComponent {
	@Input() errorGroupDetails: ErrorGroupDTO;
	protected readonly environment = environment;
	private readonly errorGroupService = inject(ErrorGroupService);
	private readonly obNotificationService = inject(ObNotificationService);
	private originalTicketNumber = '';

	createTicket() {
		this.errorGroupService.createIssue(this.errorGroupDetails.errorGroupId).subscribe({
			next: errorGroupDto => {
				this.errorGroupDetails = errorGroupDto;
				this.obNotificationService.success({message: 'i18n.createTicketSuccess'});
			},
			error: error => {
				this.obNotificationService.error({message: 'i18n.createTicketNoSuccess'});
				console.error('Error createIssue:', error);
			}
		});
	}

	saveFreeText(freeText: string) {
		this.errorGroupService.updateFreeText(this.errorGroupDetails.errorGroupId, freeText).subscribe({
			next: errorGroupDto => {
				this.errorGroupDetails = errorGroupDto;
				this.obNotificationService.success({message: 'i18n.saveFreeTextSuccess'});
			},
			error: error => {
				this.obNotificationService.error({message: 'i18n.saveFreeTextNoSuccess'});
				console.error('Error updateFreeText:', error);
			}
		});
	}

	onTicketNumberFocus(ticketNumber: string) {
		this.originalTicketNumber = ticketNumber || '';
	}

	onTicketNumberChange(ticketNumber: string) {
		const newValue = ticketNumber?.trim() || '';
		const originalValue = this.originalTicketNumber?.trim() || '';

		if (newValue !== originalValue) {
			this.errorGroupService.updateTicketNumber(this.errorGroupDetails.errorGroupId, newValue).subscribe({
				next: errorGroupDto => {
					this.errorGroupDetails = errorGroupDto;
					this.originalTicketNumber = errorGroupDto.ticketNumber || '';
					this.obNotificationService.success({message: 'i18n.updateTicketNumberSuccess'});
				},
				error: error => {
					this.obNotificationService.error({message: 'i18n.updateTicketNumberNoSuccess'});
					console.error('Error updateTicketNumber:', error);
				}
			});
		}
	}

	viewTicket() {
		if (this.errorGroupDetails.ticketNumber) {
			const url = environment.TICKETING_SYSTEM_URL.replace('{ticketNumber}', this.errorGroupDetails.ticketNumber);
			window.open(url, '_blank');
		}
	}

}
