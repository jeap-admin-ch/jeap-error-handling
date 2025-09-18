import {Component, inject, OnInit} from '@angular/core';
import {ErrorGroupService} from "../shared/errorgroupservice/error-group.service";
import {ActivatedRoute, Router} from "@angular/router";
import {ObButtonDirective, ObNotificationService} from "@oblique/oblique";
import {ErrorGroupDTO} from "../shared/errorgroupservice/error-group.model";
import {ErrorGroupDetailsHeaderComponent} from "./error-group-details-header/error-group-details-header.component";
import {
    ErrorGroupDetailsListFilterComponent
} from "./error-group-details-list-filter/error-group-details-list-filter.component";
import {ErrorGroupDetailsListComponent} from "./error-group-details-list/error-group-details-list.component";
import {NgIf} from "@angular/common";
import {MatButton} from "@angular/material/button";
import {TranslateModule} from "@ngx-translate/core";
import {ErrorGroupDetailsListSearchFormDto} from "../shared/errorservice/error.model";


@Component({
    selector: 'app-error-group-details',
    standalone: true,
    imports: [
        ErrorGroupDetailsHeaderComponent,
        ErrorGroupDetailsListFilterComponent,
        ErrorGroupDetailsListComponent,
        NgIf,
        MatButton,
        TranslateModule,
        ObButtonDirective
    ],
    templateUrl: './error-group-details.component.html',
    styleUrl: './error-group-details.component.scss'
})
export class ErrorGroupDetailsComponent implements OnInit {
    private errorGroupService = inject(ErrorGroupService);
    private route = inject(ActivatedRoute);
    private readonly obNotificationService = inject(ObNotificationService);

    errorGroupDetails: ErrorGroupDTO;
    searchCriteria: ErrorGroupDetailsListSearchFormDto;

    ngOnInit(): void {
        const errorGroupId = this.route.snapshot.paramMap.get('errorGroupId');
        if (errorGroupId) {
            this.errorGroupService.getDetailsByGroupId(errorGroupId).subscribe({
                next: value => this.errorGroupDetails = value,
                error: () => this.obNotificationService.error({title: 'Upps!', message: 'Error loading details.'})
            })
        }
    }

    back(): void {
		window.history.back();
    }

    onSearch(filterValues: any) {
        console.log("Search clicked with values:", filterValues);
        this.searchCriteria = filterValues;
        console.log("Updated search criteria:", this.searchCriteria);
    }

    onReset() {
        console.log("Reset clicked");
    }
}
