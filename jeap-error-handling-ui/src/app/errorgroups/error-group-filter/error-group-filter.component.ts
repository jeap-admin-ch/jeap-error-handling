import {Component, computed, EventEmitter, OnInit, Output, signal} from '@angular/core';
import {FormBuilder, FormControl, FormGroup, ReactiveFormsModule} from "@angular/forms";
import {ObButtonDirective, ObCheckboxDirective, ObFormFieldDirective, ObSelectDirective} from "@oblique/oblique";
import {MatDatepicker, MatDatepickerInput, MatDatepickerToggle} from "@angular/material/datepicker";
import {MatCheckbox} from "@angular/material/checkbox";
import {MatError, MatFormField, MatInput, MatLabel, MatSuffix} from "@angular/material/input";
import {MatOption, MatSelect} from "@angular/material/select";
import {MatIcon} from "@angular/material/icon";
import {TranslateModule} from "@ngx-translate/core";
import {MatButton, MatIconButton} from "@angular/material/button";
import {DropDownElement} from "../../shared/models/drop-down-element.model";
import {ErrorService} from "../../shared/errorservice/error.service";
import {NgForOf, NgIf} from "@angular/common";
import {Router} from "@angular/router";
import {MatAutocomplete, MatAutocompleteTrigger} from "@angular/material/autocomplete";
import {endOfDay, startOfDay} from "date-fns";

@Component({
  selector: 'app-error-group-filter',
  standalone: true,
	imports: [
		ObCheckboxDirective,
		ObFormFieldDirective,
		MatFormField,
		MatDatepickerToggle,
		MatDatepicker,
		MatDatepickerInput,
		ReactiveFormsModule,
		MatCheckbox,
		MatInput,
		MatSuffix,
		MatLabel,
		MatSelect,
		MatOption,
		MatIcon,
		TranslateModule,
		MatIconButton,
		NgForOf,
		NgIf,
		ObSelectDirective,
		MatButton,
		ObButtonDirective,
		MatAutocomplete,
		MatAutocompleteTrigger,
		MatError
	],
  templateUrl: './error-group-filter.component.html',
  styleUrl: './error-group-filter.component.scss'
})

export class ErrorGroupFilterComponent implements OnInit {
	@Output() searchClicked = new EventEmitter<void>();
	searchFilterFormGroup: FormGroup;
	isLoadingResults: boolean;

	/**
	 * Signal holding the list of error codes for the dropdown.
	 * Populated asynchronously from the error service.
	 */
	dropDownErrorCodes= signal<DropDownElement[]>([]);
	dropDownSources = signal<DropDownElement[]>([]);

	/**
	 * Signal holding the list of event names.
	 * Populated asynchronously from the error service.
	 */
	messageTypes = signal<string[]>([]);

	filteredMessageTypes = computed(() =>
		this.messageTypeControl.value
			? this.messageTypes().filter(event =>
				event.toLowerCase().includes((this.messageTypeControl.value || '').toLowerCase())
			)
			: this.messageTypes()
	);


	constructor(private fb: FormBuilder,
				private router: Router,
				private readonly errorService: ErrorService) {
		this.searchFilterFormGroup = this.fb.group({
			noTicket: [false],
			dateFrom: [null],
			dateTo: [null],
			source: [''],
			messageType: [''],
			dropDownErrorCode: [null],
			jiraTicket: [''],
		}) as FormGroup<{
			noTicket: FormControl<boolean>;
			dateFrom: FormControl<string | null>;
			dateTo: FormControl<string | null>;
			source: FormControl<string | null>;
			messageType: FormControl<string | null>;
			dropDownErrorCode: FormControl<string | null>;
			jiraTicket: FormControl<string | null>;
		}>;
	}

	ngOnInit(): void {
        this.errorService.getAllErrorCodes().subscribe(errorCodes => {
			const elements = errorCodes.map(errorCode => ({
				value: errorCode.valueOf(),
				viewValue: errorCode.valueOf()
			}));
			this.dropDownErrorCodes.set(elements);
		});

		this.errorService.getAllEventNames().subscribe(eventNames => {
			this.messageTypes.set(eventNames);
		});

		this.errorService.getAllEventSources().subscribe(eventSources => {
			const sources = eventSources.map(source => ({
				value: source.valueOf(),
				viewValue: source.valueOf()
			}));
			this.dropDownSources.set(sources);
		})

    }

	search(): void {
		const formValue = { ...this.searchFilterFormGroup.value };
		formValue.dateFrom = this.convertDateFormControl(this.searchFilterFormGroup.get('dateFrom') as FormControl, startOfDay);
		formValue.dateTo = this.convertDateFormControl(this.searchFilterFormGroup.get('dateTo') as FormControl, endOfDay);

		this.searchClicked.emit(formValue);
	}

	private convertDateFormControl = (formControl: FormControl, dateTransformer: (date: Date) => Date): string =>
		formControl.value ? dateTransformer(new Date(formControl.value)).toISOString() : null

	// Getters for easy access to form controls
	get formControlDropDownErrorCode(): FormControl {
		return this.searchFilterFormGroup.get('dropDownErrorCode') as FormControl<string | null>;
	}

	get formControlSource(): FormControl {
		return this.searchFilterFormGroup.get('source') as FormControl<string | null>;
	}

	get formControlJiraTicket(): FormControl {
		return this.searchFilterFormGroup.get('jiraTicket') as FormControl<string | null>;
	}

	get messageTypeControl(): FormControl {
		return this.searchFilterFormGroup.get('messageType') as FormControl<string | null>;
	}
}
