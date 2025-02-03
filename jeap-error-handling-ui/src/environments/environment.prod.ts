// This configuration will use a the CBCD keycloak server from the cloud
// Currently there is only one keycloak instance with the PAMS plugin
// It will automatically be used on the cloud, but you can also start
// it using `ng serve --prod`

import {QdAppSetup, QdAuthConfigServerSide, QdLogLevel} from '@quadrel-services/qd-auth';

export const appSetup: QdAppSetup = {
	production: true,
	serviceEndpoint: '/error-handling/api'
};

export const authConfig: QdAuthConfigServerSide = {
	configPathSegment: '/configuration',
	logLevel: QdLogLevel.Warn,
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
	oidc: {
		debug: authConfig.logLevel
	}
};
