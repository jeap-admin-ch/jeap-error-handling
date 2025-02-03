import {Injectable} from "@angular/core";
import {ObNotificationService, ObENotificationType} from "@oblique/oblique";
import {Observable, throwError} from "rxjs";

@Injectable({
	providedIn: 'root'
})
export class NotifierService {

	constructor(private readonly notificationService: ObNotificationService) {
		notificationService.clearAllOnNavigate = true;
	}

	notifySuccess(titleKey: string, messageKey: string) {
		return () => {
			this.notificationService.send({
				channel: 'oblique',
				message: messageKey,
				title: titleKey,
				type: ObENotificationType.SUCCESS
			});
		};
	}

	notifyFailure(titleKey: string, messageKey: string): (any) => Observable<never> {
		return errorMessage => {
			this.showFailureNotification(errorMessage, messageKey, titleKey);
			return throwError(errorMessage);
		}
	}

	showFailureNotification(errorMessage: string, messageKey: string, titleKey: string) {
		this.notificationService.send({
			channel: 'oblique',
			message: messageKey,
			messageParams: {
				errorMessage: errorMessage,
			},
			type: ObENotificationType.ERROR,
			title: titleKey
		});
	}
}
