import {Component, Input} from '@angular/core';
import {ErrorGroupDTO} from "../../shared/errorgroupservice/error-group.model";
import {ObButtonDirective, ObFormFieldDirective} from "@oblique/oblique";
import {MatFormFieldModule, MatLabel} from "@angular/material/form-field";
import {MatInput} from "@angular/material/input";
import {MatButton} from "@angular/material/button";
import {MatCard, MatCardContent, MatCardHeader} from "@angular/material/card";
import {TranslateModule} from "@ngx-translate/core";

@Component({
  selector: 'app-error-group-details-header',
  standalone: true,
	imports: [
		MatFormFieldModule,
		ObFormFieldDirective,
		MatLabel,
		MatInput,
		MatButton,
		MatCard,
		MatCardContent,
		MatCardHeader,
		ObButtonDirective,
		TranslateModule
	],
  templateUrl: './error-group-details-header.component.html',
  styleUrl: './error-group-details-header.component.scss'
})
export class ErrorGroupDetailsHeaderComponent {
	@Input() errorGroupDetails: ErrorGroupDTO;

	createTicket() {
		console.log("Integration Jira-Ticket/Create Jira Ticket wird in Story https://jira.bit.admin.ch/browse/JEAP-5459 umgesetzt")
	}

	isCreateButtonDisabled() {
		return (this.errorGroupDetails.ticketNumber === null) || (this.errorGroupDetails.ticketNumber === '');
	}

}
