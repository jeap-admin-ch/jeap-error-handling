/**
 * Interface representing an element for a dropdown menu.
 *
 * This interface defines the structure of a dropdown element, which includes:
 * - `value`: The internal value of the dropdown option, used for logic or data binding.
 * - `viewValue`: The user-friendly label displayed in the dropdown menu.
 */
export interface DropDownElement {
	value: string;
	viewValue: string;
}
