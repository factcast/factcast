+++
draft = false
title = "1"
weight = -1

[menu.main]
parent = "Organisation/ShipperAdminUpgraded"
identifier = "Organisation/ShipperAdminUpgraded/1"
+++

* initial draft

## Schema
```json
{
  "type": "object",
  "additionalProperties": true,
  "properties": {
    "userId": {
      "type": "string"
    },
    "timestamp": {
      "type": "string"
    },
    "actorUserId": {
      "type": "string"
    },
    "organisationId": {
      "type": "string"
    },
    "powerOfAttorney": {
      "type": "object",
      "properties": {
        "documentId": {
          "type": "string"
        },
        "s3Location": {
          "type": "object",
          "properties": {
            "key": {
              "type": "string"
            },
            "bucket": {
              "type": "string"
            },
            "region": {
              "type": "string"
            }
          },
          "required": [
            "key",
            "bucket",
            "region"
          ]
        }
      },
      "required": [
        "documentId",
        "s3Location"
      ]
    },
    "companyRegisterExtract": {
      "type": "object",
      "properties": {
        "documentId": {
          "type": "string"
        },
        "s3Location": {
          "type": "object",
          "properties": {
            "key": {
              "type": "string"
            },
            "bucket": {
              "type": "string"
            },
            "region": {
              "type": "string"
            }
          },
          "required": [
            "key",
            "bucket",
            "region"
          ]
        }
      },
      "required": [
        "documentId",
        "s3Location"
      ]
    }
  },
  "required": [
    "userId",
    "timestamp",
    "actorUserId",
    "organisationId",
    "powerOfAttorney",
    "companyRegisterExtract"
  ]
}
```

## Examples

### simple.json~
```json
{
  "userId": "404074d2-79f5-4df0-85af-d63d21d0ae01",
  "timestamp": "2020-02-17T07:53:20.103+0000",
  "actorUserId": "3bd5281c-289b-4ee3-b2f5-eb5690919e45",
  "organisationId": "a883a348-d614-4b77-a9fe-b2683e13c9f7",
  "powerOfAttorney": {
    "documentId": "f3cca0db-d15d-47f3-a8df-96fb5c8a159b",
    "s3Location": {
      "key": "foo",
      "bucket": "bucket",
      "region": "eu-central-1"
    }
  },
  "companyRegisterExtract": {
    "documentId": "f3cca0db-d15d-47f3-a8df-96fb5c8a159b",
    "s3Location": {
      "key": "foo",
      "bucket": "bucket",
      "region": "eu-central-1"
    }
  }
}
```
### simple.json
```json
{
  "userId": "404074d2-79f5-4df0-85af-d63d21d0ae01",
  "timestamp": "2020-02-17T07:53:20.103+0000",
  "actorUserId": "3bd5281c-289b-4ee3-b2f5-eb5690919e45",
  "organisationId": "a883a348-d614-4b77-a9fe-b2683e13c9f7",
  "powerOfAttorney": {
    "documentId": "f3cca0db-d15d-47f3-a8df-96fb5c8a159b",
    "s3Location": {
      "key": "foo",
      "bucket": "bucket",
      "region": "eu-central-1"
    }
  },
  "companyRegisterExtract": {
    "documentId": "f3cca0db-d15d-47f3-a8df-96fb5c8a159b",
    "s3Location": {
      "key": "foo",
      "bucket": "bucket",
      "region": "eu-central-1"
    }
  }
}
```