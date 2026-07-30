import {ObEPamsEnvironment, ObMasterLayoutConfig} from '@oblique/oblique';
import {configureServiceNavigation, PamsConfig} from './pams-header-config';

describe('configureServiceNavigation', () => {

	let masterLayoutConfig: ObMasterLayoutConfig;

	beforeEach(() => {
		masterLayoutConfig = new ObMasterLayoutConfig();
	});

	function configure(config: PamsConfig) {
		return configureServiceNavigation(config, masterLayoutConfig);
	}

	function serviceNavigation() {
		return masterLayoutConfig.header.serviceNavigation;
	}

	it('should map the pams environment when pams is enabled', () => {
		expect(configure({pamsEnabled: true, pamsEnvironment: 'DEV'})).toEqual(ObEPamsEnvironment.DEV);
		expect(configure({pamsEnabled: true, pamsEnvironment: 'REF'})).toEqual(ObEPamsEnvironment.REF);
		expect(configure({pamsEnabled: true, pamsEnvironment: 'TEST'})).toEqual(ObEPamsEnvironment.TEST);
		expect(configure({pamsEnabled: true, pamsEnvironment: 'ABN'})).toEqual(ObEPamsEnvironment.ABN);
		expect(configure({pamsEnabled: true, pamsEnvironment: 'PROD'})).toEqual(ObEPamsEnvironment.PROD);
	});

	it('should treat a missing pamsEnabled property as enabled', () => {
		expect(configure({pamsEnvironment: 'REF'})).toEqual(ObEPamsEnvironment.REF);
		expect(serviceNavigation().displayAuthentication).toBe(true);
	});

	it('should display the pams backed controls when pams is enabled', () => {
		configure({pamsEnabled: true, pamsEnvironment: 'REF'});

		expect(serviceNavigation().displayAuthentication).toBe(true);
		expect(serviceNavigation().displayProfile).toBe(true);
		expect(serviceNavigation().displayMessage).toBe(true);
		expect(serviceNavigation().handleLogout).toBe(true);
		expect(serviceNavigation().displayLanguages).toBe(true);
	});

	it('should not provide a pams environment when pams is disabled', () => {
		expect(configure({pamsEnabled: false, pamsEnvironment: 'PROD'})).toBeNull();
	});

	it('should hide the pams backed controls but keep the languages when pams is disabled', () => {
		configure({pamsEnabled: false});

		expect(serviceNavigation().displayAuthentication).toBe(false);
		expect(serviceNavigation().displayProfile).toBe(false);
		expect(serviceNavigation().displayMessage).toBe(false);
		expect(serviceNavigation().displayInfo).toBe(false);
		expect(serviceNavigation().displayApplications).toBe(false);
		expect(serviceNavigation().handleLogout).toBe(false);
		expect(serviceNavigation().displayLanguages).toBe(true);
	});

	it('should not warn about an unknown environment when pams is disabled', () => {
		const warn = jest.spyOn(console, 'warn').mockImplementation();

		configure({pamsEnabled: false, pamsEnvironment: ''});

		expect(warn).not.toHaveBeenCalled();
		warn.mockRestore();
	});

	it('should warn about an unknown environment when pams is enabled', () => {
		const warn = jest.spyOn(console, 'warn').mockImplementation();

		expect(configure({pamsEnabled: true, pamsEnvironment: ''})).toBeNull();

		expect(warn).toHaveBeenCalled();
		warn.mockRestore();
	});
});
