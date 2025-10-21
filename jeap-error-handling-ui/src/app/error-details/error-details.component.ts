import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, ParamMap} from '@angular/router';
import {catchError, switchMap} from 'rxjs/operators';
import {ErrorService} from '../shared/errorservice/error.service';
import {ErrorDTO} from '../shared/errorservice/error.model';
import {Observable} from 'rxjs';
import {NotifierService} from '../shared/notifier/notifier.service';
import {Location} from '@angular/common';
import {LogDeepLinkService} from '../shared/logdeeplink/logdeeplink.service';
import { DialogService } from '../shared/dialog/dialog.service';
import {TranslateService} from '@ngx-translate/core';
import {environment} from '../../environments/environment';
import {ObNotificationService} from "@oblique/oblique";

@Component({
	selector: 'error-details',
	templateUrl: './error-details.component.html',
	styleUrls: ['./error-details.component.css'],
	standalone: false
})
export class ErrorDetailsComponent implements OnInit {

	errorDto$: Observable<ErrorDTO>;
	causingEventPayload$: Observable<string>;
	actionsDisabled: boolean;
	logDeepLink: string;
	logDeepLinkTemplate: string;
	displayedColumns: string[] = ['action', 'created', 'givenName', 'familyName', 'extId', 'subject', 'authContext'];

	constructor(
		private readonly errorService: ErrorService,
		private readonly route: ActivatedRoute,
		private readonly notifierService: NotifierService,
		private readonly location: Location,
		private readonly logDeepLinkService: LogDeepLinkService,
		private readonly dialogService: DialogService,
		private readonly translateService: TranslateService,
		private readonly obNotificationService: ObNotificationService
	) { }

	ngOnInit() {
		this.errorDto$ = this.route.paramMap.pipe(switchMap((params: ParamMap) => {
			this.actionsDisabled = false;
			return this.errorService.getErrorDetails(params.get('errorId'));
		}), catchError(
			this.notifierService.notifyFailure('i18n.errorhandling.details.load', 'i18n.errorhandling.failure')));

		this.causingEventPayload$ = this.route.paramMap.pipe(switchMap((params: ParamMap) => {
			return this.errorService.getCausingEventPayload(params.get('errorId'));
		}), catchError(
			this.notifierService.notifyFailure('i18n.errorhandling.details.load-payload', 'i18n.errorhandling.failure')));

		this.logDeepLinkService.getLogDeepLink().subscribe(template => {
			this.logDeepLinkTemplate = template;
		});
	}

	resendRow(row: ErrorDTO) {
		this.actionsDisabled = true;
		if (row.errorState.includes('DELETED')) {
			this.dialogService.confirm(
				this.translateService.instant('i18n.errorhandling.confirm.closed-error')).subscribe(confirmed => {
				if (confirmed) {
					this.retry(row.id);
				}
			});
		} else {
			this.retry(row.id);
		}
	}

	retry(id: string) {
		this.errorService.retry(id).subscribe(
			this.notifierService.notifySuccess('i18n.errorhandling.action.retry', 'i18n.errorhandling.action.success'),
			this.notifierService.notifyFailure('i18n.errorhandling.action.retry', 'i18n.errorhandling.failure'));
	}

	delete(errorId: string) {
		this.actionsDisabled = true;
		this.dialogService.getClosingReason().subscribe(reason => {
			if (reason != null) {
				this.errorService.delete(errorId, reason).subscribe(
					this.notifierService.notifySuccess('i18n.errorhandling.action.delete', 'i18n.errorhandling.action.success'),
					this.notifierService.notifyFailure('i18n.errorhandling.action.delete', 'i18n.errorhandling.failure'));
			} else {
				this.actionsDisabled = false;
			}
		});
	}


	back() {
		this.location.back();
	}

	openDeepLink(traceId: string) {
		this.logDeepLink = this.logDeepLinkService.replaceTraceId(this.logDeepLinkTemplate, traceId);
		window.open(this.logDeepLink, '_blank');
	}

	generateTicketSystemUrl(ticketNumber: string): string {
		return environment.TICKETING_SYSTEM_URL.replace('{ticketNumber}', ticketNumber);
	}

	copyStacktrace(stacktrace: string) {
		if (stacktrace) {
			navigator.clipboard.writeText(stacktrace).then(
				() => this.obNotificationService.success({message: 'i18n.copyStacktraceSuccess'})
			);
		}
	}
}
