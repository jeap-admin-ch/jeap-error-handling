import {AbstractControl, FormControl, ValidatorFn} from "@angular/forms";


/**
 * Abstract base class for components.
 */
export abstract class BaseComponent {

	/**
	 * Converts the value of a FormControl to an ISO string after applying a date transformation.
	 *
	 * @param formControl - The FormControl containing the date value.
	 * @param dateTransformer - A function to transform the Date object.
	 * @returns The transformed date as an ISO string, or null if the value is not set.
	 */
	protected convertDateFormControl(formControl: FormControl, dateTransformer: (date: Date) => Date): string | null {
		return formControl.value ? dateTransformer(new Date(formControl.value)).toISOString() : null;
	}

	/**
	 * Returns a validator function that checks if the control's value is a valid regular expression.
	 *
	 * @returns A ValidatorFn that returns an error object if the regex is invalid, or null if valid.
	 */
	regexValidator(): ValidatorFn {
		return (control: AbstractControl): { [key: string]: any } | null => {
			const regexString = control.value;
			if (!regexString) {
				return null;
			}
			try {
				// tslint:disable-next-line:no-unused-expression
				new RegExp(regexString);
				return null;
			} catch {
				return {'invalidRegex': {value: regexString}};
			}
		};
	}


}
