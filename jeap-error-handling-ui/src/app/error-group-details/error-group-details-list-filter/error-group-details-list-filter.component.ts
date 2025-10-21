import {Component, EventEmitter, Output} from '@angular/core';
import {MatCard, MatCardContent} from "@angular/material/card";
import {MatButton, MatIconButton} from "@angular/material/button";
import {MatDatepicker, MatDatepickerInput, MatDatepickerToggle} from "@angular/material/datepicker";
import {MatError, MatFormField, MatLabel, MatSuffix} from "@angular/material/form-field";
import {MatInput} from "@angular/material/input";
import {ObButtonDirective} from "@oblique/oblique";
import {FormBuilder, FormControl, FormGroup, ReactiveFormsModule} from "@angular/forms";
import {TranslateModule} from "@ngx-translate/core";
import {endOfDay, startOfDay} from "date-fns";
import {BaseComponent} from "../../shared/BaseComponent";
import {MatIcon} from "@angular/material/icon";
import {NgIf} from "@angular/common";

@Component({
	selector: 'app-error-group-details-list-filter',
	standalone: true,
	imports: [
		MatCard,
		MatCardContent,
		MatButton,
		MatDatepicker,
		MatDatepickerInput,
		MatDatepickerToggle,
		MatFormField,
		MatInput,
		MatSuffix,
		ObButtonDirective,
		ReactiveFormsModule,
		TranslateModule,
		MatError,
		MatIcon,
		MatIconButton,
		MatLabel,
		NgIf
	],
	templateUrl: './error-group-details-list-filter.component.html',
	styleUrls: ['./error-group-details-list-filter.component.scss']
})
export class ErrorGroupDetailsListFilterComponent extends BaseComponent {
	@Output() searchClicked = new EventEmitter<void>();
	@Output() reset = new EventEmitter<void>();

	searchFilterFormGroup: FormGroup;
	isLoadingResults: boolean;

	constructor(private fb: FormBuilder) {
		super();
		this.searchFilterFormGroup = this.fb.group({
			dateFrom: [null],
			dateTo: [null],
			stacktracePattern: [null, this.regexValidator()],
			messagePattern: [null, this.regexValidator()]
		}) as FormGroup<{
			dateFrom: FormControl<string | null>;
			dateTo: FormControl<string | null>;
			stacktracePattern: FormControl<string | null>;
			messagePattern: FormControl<string | null>;
		}>;
	}

	get stacktraceControl(): FormControl {
		return this.searchFilterFormGroup.get('stacktracePattern') as FormControl<string | null>;
	}

	get messageControl(): FormControl {
		return this.searchFilterFormGroup.get('messagePattern') as FormControl<string | null>;
	}

	search(): void {
		const formValue = {...this.searchFilterFormGroup.value};
		formValue.dateFrom = this.convertDateFormControl(this.searchFilterFormGroup.get('dateFrom') as FormControl, startOfDay);
		formValue.dateTo = this.convertDateFormControl(this.searchFilterFormGroup.get('dateTo') as FormControl, endOfDay);
		this.searchClicked.emit(formValue);
	}

	resetClicked() {
		this.reset.emit();
	}


}
