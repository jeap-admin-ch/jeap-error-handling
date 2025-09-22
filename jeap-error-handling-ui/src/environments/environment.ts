// The file contents for the current environment will overwrite these during build.
// The build system defaults to the dev environment which uses `environment.ts`, but if you do
// `ng build --env=prod` then `environment.prod.ts` will be used instead.
// The list of which env maps to which file can be found in `angular-cli.json`.

// This configuration will use a local mock server for authentication.
// To use a local keycloak instance with PAMS use the environment localKeycloak
// and start the keycloak from the jeap-pams-keycloak project
import {QdAppSetup, QdAuthConfigServerSide, QdLogLevel} from '@quadrel-services/qd-auth';

export const appSetup: QdAppSetup = {
	production: false,
	serviceEndpoint: 'http://localhost:8072/error-handling/api'
};

export const authConfig: QdAuthConfigServerSide = {
	configPathSegment: '/configuration',
	logLevel: QdLogLevel.Error,
	renewUserInfoAfterTokenRenew: true,
	silentRenew: true,
	silentRenewUrl: `${window.location.origin}/error-handling/assets/auth/silent-renew.html`,
	useAutoLogin: true
};

export const environment = {
	production: appSetup.production,
	BACKEND_SERVICE_API: appSetup.serviceEndpoint,
	CONFIGURATION_PATH: authConfig.configPathSegment,
	TICKETING_SYSTEM_URL: '',
	ISSUE_TRACKING_ENABLED: false,
	oidc: {
		debug: QdLogLevel.None
	}
};
