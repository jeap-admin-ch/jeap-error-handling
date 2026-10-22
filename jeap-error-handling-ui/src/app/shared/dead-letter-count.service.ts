import {DestroyRef, inject, Injectable} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {HttpClient} from '@angular/common/http';
import {QdAuthenticationService} from '@quadrel-enterprise-ui/auth';
import {BehaviorSubject, EMPTY, merge, Observable, of, timer} from 'rxjs';
import {catchError, distinctUntilChanged, filter, map, switchMap, tap} from 'rxjs/operators';
import {environment} from '../../environments/environment';

interface DeadLetterCountResponse {
	pendingMessageCount: number;
}

/**
 * Polls the number of messages pending on the dead-letter topic and exposes it so it can be shown on every
 * page. refresh() lets a page that just triggered a reactivation update the count immediately instead of
 * waiting for the next poll. The poll interval is read from the backend, so it stays in sync with the
 * interval at which the backend itself re-determines the count (jeap.errorhandling.dead-letter-count.*).
 */
@Injectable({
	providedIn: 'root'
})
export class DeadLetterCountService {

	private readonly url: string = environment.BACKEND_SERVICE_API + '/deadletter/count';
	private readonly updateFrequencyUrl: string = environment.BACKEND_SERVICE_API + '/configuration/dead-letter-count-update-frequency';
	private readonly countSubject = new BehaviorSubject<number | null>(null);

	readonly count$: Observable<number | null> = this.countSubject.asObservable();

	private readonly destroyRef = inject(DestroyRef);
	private authenticated = false;

	constructor(private readonly http: HttpClient, authenticationService: QdAuthenticationService) {
		// Nothing is requested before the user is authenticated, otherwise every request would fail with a 401
		authenticationService.isAuthenticated$.pipe(
			distinctUntilChanged(),
			tap(authenticated => this.authenticated = authenticated),
			switchMap(authenticated => authenticated ? this.poll() : EMPTY),
			takeUntilDestroyed(this.destroyRef)
		).subscribe(count => this.countSubject.next(count));
	}

	/** Emits the current count immediately, then re-fetches it at the interval configured in the backend. */
	private poll(): Observable<number | null> {
		const pollIntervalMs$ = this.http.get(this.updateFrequencyUrl, { responseType: 'text' }).pipe(
			map(value => Number(value)),
			// without a valid interval from the backend there is no polling, the count is still refreshed on demand
			filter(pollIntervalMs => pollIntervalMs > 0),
			catchError(() => EMPTY)
		);
		return merge(
			this.fetchCount(),
			// the first poll is due after one interval; switchMap drops a still-running request when the next starts
			pollIntervalMs$.pipe(
				switchMap(pollIntervalMs => timer(pollIntervalMs, pollIntervalMs)),
				switchMap(() => this.fetchCount())
			)
		);
	}

	refresh(): void {
		if (!this.authenticated) {
			return;
		}
		this.fetchCount().subscribe(count => this.countSubject.next(count));
	}

	private fetchCount(): Observable<number | null> {
		return this.http.get<DeadLetterCountResponse>(this.url).pipe(
			// the backend reports a negative sentinel before it has determined the count for the first time;
			// treat that the same as a failed fetch instead of showing it as "0 pending"
			map(response => response.pendingMessageCount >= 0 ? response.pendingMessageCount : null),
			catchError(() => of(null))
		);
	}
}
