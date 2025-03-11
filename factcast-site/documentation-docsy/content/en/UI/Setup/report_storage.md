---
title: "Report Storage"
type: docs
weight: 300
description: "Persist the results of your fact queries for future analysis."
---

The FactCast-Server UI comes with a report page that saves the result of your query for future usage. Generated reports
are only visible for the user that created them and can be downloaded and deleted at any time.

### Filesystem Storage

Per default reports are stored on the filesystem of the instance that is running the FactCast-Server UI. The path can be
configured using the `factcast.ui.reports.path` property.
This setup has the downside that reports are lost when the instance is restarted or the filesystem is wiped, as well as
that large amounts of reports can fill up the disk. Because of this, it is recommended to use an external ReportStore.

### S3 Report Store

The recommended way to use the FactCast-Server UI Reports page is to store reports in an S3 bucket. To enable this, you
can use the `factcast-server-ui-s3-reportstore` module by importing it as a dependency in your project.

```xml
<dependency>
    <groupId>org.factcast</groupId>
    <artifactId>factcast-server-ui-s3-reportstore</artifactId>
    <version>${factcast.version}</version>
</dependency>
```

When setting up the S3 Bucket ensure the following permissions are granted to your server instance:

```json
[
  {
      effect: Effect.ALLOW,
      actions: [
        's3:GetObject',
        's3:GetObjectVersion',
        's3:PutObject',
        's3:DeleteObject',
      ],
      resources: [`arn:aws:s3:::<reportBucketName>`],
    },
    {
      effect: Effect.ALLOW,
      actions: ['s3:ListBucket'],
      resources: [`arn:aws:s3:::<reportBucketName>`],
    }
]
```

Finally in the `application.properties` file, configure the following properties:

| Property                    | Description                                                                     | Default |
| --------------------------- | :------------------------------------------------------------------------------ | :------ |
| factcast.ui.report.store.s3 | The name of the S3 Bucket in which the reports are stored by the S3ReportStore. | -/-     |
