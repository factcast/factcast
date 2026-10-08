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

Playwrite offers this generative mode which records interactions with the ui and turns them into accessor, which can be
a great help setting up new integration tests. Make sure to remove <test> scope from the playwrite dependency before.
`mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="codegen localhost:8080"`

## Report Generation

The UI offers a report generation feature that can be used to generate and save reports based on certain event criteria.
These reports can be stored either in S3 (`S3ReportStore`) or in your local filesystem (`FileSystemReportStore`).

If you want to use the `S3ReportStore`, you need to provide the configuration property of `factcast.ui.report.store.s3`.
If you chose to use the `FileSystemReportStore`, you need to set the configuration property of
`factcast.ui.report.store.path`.
