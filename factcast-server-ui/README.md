## Local deployment

The UI can be started locally by executing the run configuration located in `.run/ExampleUiServer.run.xml`.

To log-in use any username and `security_disabled` as password. The page will be provided under `localhost:8080`.
You now can make changes to the code that will be reflected in the frontend once the service is re-compiled, which
is done automatically.

## Integration / End-to-End tests

E2E Tests are not automatically executed as part of failsafe. In order to run them, you'd need to add
`-Dui` to your maven run.

Optionally, you could add `-Dui.watch` or `-Dui.record` to either see the tests execute, or to record their execution
into videos that you can find in the target folder, respectively. 



