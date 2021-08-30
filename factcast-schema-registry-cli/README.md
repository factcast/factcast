factcast-schema-registry-cli
============================

This CLI provides a convenient way to create a suitable Schema Registry for your Factcast installation.
It will give you the ability to validate events against examples and to make sure that there's always an upcast and if
necessary a downcast transformation.

Synopsis
--------
### Validation
```
java -jar fc-schema-cli.jar validate [-p dir] [-w file] [-s]
```

### Building
```
java -jar fc-schema-cli.jar build [-p dir] [-o dir] [-w file] [-s]
```

Options
-------
| Short Option | Long Option    | Description                                                                            |
|--------------|----------------|----------------------------------------------------------------------------------------|
| `-p`         | `--base-path`  | Directory where your source files live. Defaults to the current directory.             |
| `-o`         | `--output`     | Output directory of the registry. If omitted creates `output` in the current directory. |
| `-w`         | `--white-list` | Path to an whitelist file. Optional.  |
| `-s`         | `--schema-strip-titles` |  Remove the 'title' attribute from JSON schema files |

Whitelisting
------------
There is support for a whitelist to explicitly state which events should be validated or build. A whitelist is a text file
containing white list entries on each line. An entry can contain [file globs](https://javapapers.com/java/glob-with-java-nio/).

Here are some examples:

| Wildcard Entry                       | Description                                                                                            |
|--------------------------------------|--------------------------------------------------------------------------------------------------------|
| `/ordering/OrderReceived/versions/1` | matches the _OrderReceived_ event version 1 in the _ordering_ namespace. Most specific wildcard entry. |
| `/ordering/Order*/versions/1`        | matches version 1 of all events in the _ordering_ namespace starting with _Order_                      |
| `/ordering/**`                       | all events from the ordering namespace                                                                 |

To see which events where considered for validation or building set the log level to _debug_.

Logging
-------
The default application log level is _info_. More application details are available on _trace_ level. To change the log level use the `log.level` property as follows:

```
java -Dlog.level=trace -jar fc-schema-cli.jar validate
```
