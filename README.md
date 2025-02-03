# jEAP Error Handling Service Core Logic Library
Error Handling Service supports error handling patterns for errors, i.e. retry for temporary issues, persistence
and retry/handling for permanent errors.

## Installing / Getting started

Normally you will not use this project directly, but instead set up your own error service depending on this common library. Check the documentation in confluence for details.

## Developing

### Start the error service locally

1. Publish a local snapshot 
2. Then use the jme-messaging-example with a dependency on the snapshot for tests

For UI: 
1. Start the Docker Stuff in jme-messaging-example
2. Start the Auth (OAuth-Mock Server) in jme-messaging-example under profile 'local'
3. Start the Error-SCS as Backend in the jme-messaging-example (profile local-ui)
4. Start the jeap-error-handling-ui as ng server (localhost:4200) in this project

### Versioning

This library needs to be versioned using [Semantic Versioning](http://semver.org/) and all changed need to be documented at [CHANGELOG.md](./CHANGELOG.md) following the format defined in [Keep a Changelog](http://keepachangelog.com/)

## Changes
Change log is available at [CHANGELOG.md](./CHANGELOG.md)

## Note

This repository is part the open source distribution of jEAP. See [github.com/jeap-admin-ch/jeap](https://github.com/jeap-admin-ch/jeap)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).
