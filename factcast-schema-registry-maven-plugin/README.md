FactCast Schema Registry Maven Plugin
=====================================

A wrapper to run the [FactCast Schema Registry CLI](https://github.com/factcast/factcast/tree/master/factcast-schema-registry-cli) within maven.

Example Configuration
---------------------
```
<plugin>
    <groupId>org.factcast</groupId>
    <artifactId>factcast-schema-registry-maven-plugin</artifactId>
    <version>0.3.7-SNAPSHOT</version>
    <configuration>
        <sourceDirectory>/path/to/source/</sourceDirectory>
        <outputDirectory>/path/to/output/</outputDirectory>
        <includedEvents>
            <includedEvent>/ordering/**</includedEvent>
            <includedEvent>/shipping/ShippingStarted/versions/1</includedEvent>
            <includedEvent>/shipping/ShippingInterupted/**</includedEvent>
        </includedEvents>
        <schemaStripTitles>true</schemaStripTitles>
    </configuration>
</plugin>
```

Configuration Options
---------------------
The following configuration options are supported:

| Configuration Option | Default | Description                                                                 | Example                                                                                                                                                                                                                                   |
|----------------------|---------|-----------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sourceDirectory`    | `${project.basedir}/src/main/resources` | location of the source schema registry                                      | `<sourceDirectory>/path/to/registry</sourceDirectory>`                                                                                                                                                                                    |
| `outputDirectory`    | `${project.build.directory}/registry`   | where to write results to                                                   | `<outputDirectory>/path/to/output_directory</outputDirectory>`                                                                                                                                                                            |
| `includedEvents`     | if not present all events from the source dir are considered | list of events which the schema registry CLI should consider for processing | `<includedEvents> <includedEvent>/ordering/**</includedEvent> <includedEvent>/shipping/ShippingStarted/versions/1</includedEvent> <includedEvent>/shipping/ShippingInterupted/**</includedEvent></includedEvents>` |
| `includedEvents`     | if not present all events from the source dir are considered | list of events which the schema registry CLI should consider for processing | `<includedEvents> <includedEvent>/ordering/**</includedEvent> <includedEvent>/shipping/ShippingStarted/versions/1</includedEvent> <includedEvent>/shipping/ShippingInterupted/**</includedEvent></includedEvents>` |
| `schemaStripTitles`  | _false_ | remove all 'title' attributes from the JSON schemas if set to 'true' | `<schemaStripTitles>true</schemaStripTitles>` |

Local Developer Test
--------------------
* temporarily add the config above to the plugins `pom.xml`
* execute e.g. `rm -rf ~/.m2/repository/org/factcast/factcast-schema-registry-maven-plugin/0.3.7-SNAPSHOT/ && mvn clean install && mvn org.factcast:factcast-schema-registry-maven-plugin:0.3.7-SNAPSHOT:validate`
