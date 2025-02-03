import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';

import {ErrorListPageComponent} from './pages/error-list-page/error-list-page.component';
import {ErrorDetailsPageComponent} from './pages/error-details-page/error-details-page.component';
import {
	QdApplicationRoleFilter,
	QdAuthorizationGuard,
	QdRoleFilter,
	QdRoleFilterMatcher
} from '@quadrel-services/qd-auth';
import {ErrorGroupPageComponent} from "./pages/error-group-page/error-group-page.component";
import {ForbiddenPageComponent} from "./pages/error-pages/forbidden-page/forbidden-page.component";


const roleFilter_view = QdApplicationRoleFilter.hasRole(
	QdRoleFilterMatcher.ANY,
	'error',
	'view'
);

export const roleFilter_errorgroup_edit: QdRoleFilter = QdApplicationRoleFilter.hasRole(
	QdRoleFilterMatcher.ANY,
	'errorgroup',
	'edit'
);

const roleFilter_errorgroup_view = QdApplicationRoleFilter.hasRole(
	QdRoleFilterMatcher.ANY,
	'errorgroup',
	'view'
);

const appRoutes: Routes = [
	{
		path: '',
		redirectTo: '/error-list',
		pathMatch: 'full'
	},
	{
		path: 'redirect',
		redirectTo: '/error-list',
	},
	{
		path: 'Forbidden',
		component: ForbiddenPageComponent
	},
	{
		path: 'error-list', component: ErrorListPageComponent,
		canLoad: [QdAuthorizationGuard],
		canActivate: [QdAuthorizationGuard],
		data: {
			roleFilter: [roleFilter_view]
		}
	},
	{
		path: 'error-details/:errorId', component: ErrorDetailsPageComponent,
		canLoad: [QdAuthorizationGuard],
		canActivate: [QdAuthorizationGuard],
		data: {
			roleFilter: [roleFilter_view],
		}
	},
	{
		path: 'error-group', component: ErrorGroupPageComponent,
		canLoad: [QdAuthorizationGuard],
		canActivate: [QdAuthorizationGuard],
		data: {
			roleFilter: [roleFilter_errorgroup_view]
		}
	}
];

@NgModule({
	imports: [
		RouterModule.forRoot(appRoutes)
	],
	exports: [
		RouterModule
	]
})
export class AppRoutingModule {
}
