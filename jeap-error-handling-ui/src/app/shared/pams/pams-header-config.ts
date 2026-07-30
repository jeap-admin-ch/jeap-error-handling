import {ObEPamsEnvironment, ObMasterLayoutConfig} from '@oblique/oblique';

export type QdShellHeaderWidgetEnvironment = 'DEV' | 'TEST' | 'REF' | 'ABN' | 'PROD';

/**
 * The part of the frontend configuration served by the backend that drives the ePortal/PAMS service
 * navigation of the Oblique header. `pamsEnabled` is specific to the error handling service and therefore
 * not part of the configuration model of the Quadrel auth library.
 */
export interface PamsConfig {
	pamsEnabled?: boolean;
	pamsEnvironment?: '' | QdShellHeaderWidgetEnvironment;
}

/**
 * Configures the ePortal/PAMS service navigation of the Oblique header and returns the PAMS environment to
 * provide as `OB_PAMS_CONFIGURATION`.
 *
 * When PAMS is disabled (`jeap.errorhandling.frontend.pams-enabled=false`), the returned environment is null.
 * Oblique then skips the initialisation of the service navigation entirely, so no request is sent to the
 * ePortal backend and no session timeout handling is installed. The controls backed by that backend would be
 * defunct and are hidden; the language selection is fed by the translate service and therefore kept.
 */
export function configureServiceNavigation(config: PamsConfig, masterLayoutConfig: ObMasterLayoutConfig): ObEPamsEnvironment | null {
	const serviceNavigation = masterLayoutConfig.header.serviceNavigation;
	const pamsEnabled = config.pamsEnabled !== false;

	serviceNavigation.displayLanguages = true;
	serviceNavigation.displayInfo = false;
	serviceNavigation.displayApplications = false;
	serviceNavigation.displayMessage = pamsEnabled;
	serviceNavigation.displayProfile = pamsEnabled;
	serviceNavigation.displayAuthentication = pamsEnabled;
	serviceNavigation.handleLogout = pamsEnabled;

	return pamsEnabled ? mapEnvironmentEnum(config.pamsEnvironment) : null;
}

/**
 * Maps a given QdShellHeaderWidgetEnvironment string to the corresponding ObEPamsEnvironment enumeration value.
 */
function mapEnvironmentEnum(qdEnv: '' | QdShellHeaderWidgetEnvironment): ObEPamsEnvironment {
	switch (qdEnv) {
		case 'DEV':
			return ObEPamsEnvironment.DEV;
		case 'REF':
			return ObEPamsEnvironment.REF;
		case 'ABN':
			return ObEPamsEnvironment.ABN;
		case 'TEST':
			return ObEPamsEnvironment.TEST;
		case 'PROD':
			return ObEPamsEnvironment.PROD;
		default:
			console.warn(`Unrecognized pamsEnvironment: ${qdEnv}. ServiceNavigation will not work.`);
			return null;
	}
}
