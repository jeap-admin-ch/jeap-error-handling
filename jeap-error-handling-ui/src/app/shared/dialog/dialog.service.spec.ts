
import { MatDialog } from '@angular/material/dialog';
import { DialogService } from './dialog.service';
import { of } from 'rxjs';


describe('DialogService', () => {
	let service: DialogService;
	let mockMatDialog: MatDialog;

	beforeEach(() => {
		mockMatDialog = {
			open: jest.fn().mockReturnValue({
				afterClosed: jest.fn().mockReturnValue(of(true)),
			}),
		} as unknown as MatDialog;

		service = new DialogService(mockMatDialog);
	});

	it('should be created', () => {
		expect(service).toBeTruthy();
	});

	describe('confirm', () => {
		it('should open confirmation dialog with provided message', () => {
			const message = 'Do you confirm?';
			service.confirm(message);
			expect(mockMatDialog.open).toHaveBeenCalledWith(expect.any(Function), {
				width: '500px',
				data: { message },
			});
		});

		it('should return true when user confirms', () => {
			const confirmed = service.confirm('Do you confirm?');
			confirmed.subscribe((result) => {
				expect(result).toBe(true);
			});
		});

		it('should return false when user cancels', () => {
			mockMatDialog.open = jest.fn().mockReturnValue({
				afterClosed: jest.fn().mockReturnValue(of(false)),
			});

			const confirmed = service.confirm('Do you confirm?');
			confirmed.subscribe((result) => {
				expect(result).toBe(false);
			});
		});
	});

	describe('getClosingReason', () => {
		it('should open closing reason dialog', () => {
			service.getClosingReason();
			expect(mockMatDialog.open).toHaveBeenCalledWith(expect.any(Function), {
				width: '500px',
				data: {},
			});
		});

		it('should return the reason when user selects one', () => {
			const reason = 'I am done';
			mockMatDialog.open = jest.fn().mockReturnValue({
				afterClosed: jest.fn().mockReturnValue(of(reason)),
			});

			const closingReason = service.getClosingReason();
			closingReason.subscribe((result) => {
				expect(result).toBe(reason);
			});
		});

		it('should return "Keine" when user does not select a reason', () => {
			mockMatDialog.open = jest.fn().mockReturnValue({
				afterClosed: jest.fn().mockReturnValue(of(undefined)),
			});

			const closingReason = service.getClosingReason();
			closingReason.subscribe((result) => {
				expect(result).toBe('Keine');
			});
		});
	});
});
