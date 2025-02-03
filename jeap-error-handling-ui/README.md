# JEAP Error Handling UI

## Development server

Run `ng serve` for a dev server. Navigate to `http://localhost:4200/`. The app will automatically reload if you change any of the source files.

## Code scaffolding

Run `ng generate component component-name` to generate a new component. You can also use `ng generate directive|pipe|service|class|guard|interface|enum|module`.

## Build

Run `ng build` to build the project. The build artifacts will be stored in the `dist/` directory. Use the `--prod` flag for a production build.

## Running unit tests

Run `ng test` to execute the unit tests via [Karma](https://karma-runner.github.io).

## Start the application locally
To start this application, you have to use an authentication server. There are three possibilities:
* Use the jeap-Mock server. This is the default configuration. You have to start the jeap mock server example on the same machine and then start this application using `ng serve`
* Use a local keycloak. You can check out the jeap-pams-keycloak project and start the keycloak configuration there. Then you can start this application using `ng serve --configuration=localKeycloak`
* Use the CBCD keycloak. Therefore you do not have to start a local server and you can start the application using  `ng serve --configuration=localCBCD`

