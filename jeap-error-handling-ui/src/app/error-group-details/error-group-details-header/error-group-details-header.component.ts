import {Component, inject, Input} from '@angular/core';
import {ErrorGroupDTO} from "../../shared/errorgroupservice/error-group.model";
import {ObButtonDirective, ObFormFieldDirective, ObNotificationService} from "@oblique/oblique";
import {MatFormFieldModule, MatLabel} from "@angular/material/form-field";
import {MatInput} from "@angular/material/input";
import {MatButton} from "@angular/material/button";
import {MatCard, MatCardContent, MatCardHeader} from "@angular/material/card";
import {TranslateModule} from "@ngx-translate/core";
import {FormsModule} from "@angular/forms";
import {ErrorGroupService} from "../../shared/errorgroupservice/error-group.service";

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
        TranslateModule,
        FormsModule
    ],
    templateUrl: './error-group-details-header.component.html',
    styleUrl: './error-group-details-header.component.scss'
})
export class ErrorGroupDetailsHeaderComponent {
    private readonly errorGroupService = inject(ErrorGroupService);
    private readonly obNotificationService = inject(ObNotificationService);

    @Input() errorGroupDetails: ErrorGroupDTO;

    createTicket() {
        console.log("Integration Jira-Ticket/Create Jira Ticket wird in Story https://jira.bit.admin.ch/browse/JEAP-5459 umgesetzt")
    }

    saveFreeText(freeText: string) {
        this.errorGroupService.updateFreeText(this.errorGroupDetails.errorGroupId, freeText).subscribe(
            errorGroupDto => {
                this.errorGroupDetails = errorGroupDto;
                this.obNotificationService.success("Free text updated successfully.");
            }),
            error => {
            	this.obNotificationService.error({title: 'Upps!', message: 'Error loading details.'});
                console.error('Error updateFreeText:', error);
        }

    }

}
