+++
title = "Snapshot Caching"
weight = 30
type = "docs"
+++

The component responsible for storing and managing snapshots is called the SnapshotCache.

Factus does not provide a default SnapshotCache, requiring users to make an explicit configuration choice. If a
SnapshotCache is not configured, any attempt to use snapshots will result in an UnsupportedOperationException.

By default, the SnapshotCache retains only the latest version of a particular snapshot.

There are several predefined SnapshotCache implementations available, with plans to introduce additional options in the
future.

### In-Memory SnapshotCache

For scenarios where persistence and sharing of snapshots are not necessary, and sufficient RAM is available, the
in-memory solution can be used:

```xml

<dependency>
    <groupId>org.factcast</groupId>
    <artifactId>factcast-snapshotcache-local-memory</artifactId>
</dependency>
```

Refer to the [In-Memory Properties]({{< ref "/setup/properties#inmem-snapshots">}}) for configuration details.

### In-Memory and Disk SnapshotCache

To persist snapshots on disk, consider using the following configuration:

```xml

<dependency>
    <groupId>org.factcast</groupId>
    <artifactId>factcast-snapshotcache-local-disk</artifactId>
</dependency>
```

Note that this setup is designed for single-instance applications and handles file access synchronization within the
active instance. It is not recommended for distributed application architectures.

Refer to the [In-Memory and Disk Properties]({{< ref "/setup/properties#inmemanddisk-snapshots">}}) for more
information.

### Redis SnapshotCache

For applications utilizing Redis, the Redis-based SnapshotCache offers an optimal solution to implement a
snapshotcache that can be shared between / used by many instances:

```xml

<dependency>
    <groupId>org.factcast</groupId>
    <artifactId>factcast-snapshotcache-redisson</artifactId>
</dependency>
```

This option supports multiple instances of the same application, making it suitable for distributed environments. By
default, this cache automatically deletes stale snapshots after 90 days.

For further details, see the [Redis Properties]({{< ref "/setup/properties#redissnapshots">}}).

### JDBC SnapshotCache

For applications utilizing a JDBC storage solution, the JDBC-based SnapshotCache offers an optimal solution:

```xml

<dependency>
    <groupId>org.factcast</groupId>
    <artifactId>factcast-snapshotcache-jdbc</artifactId>
</dependency>
```

This option also enables the use of multiple instances of the same application, facilitating distributed environments by
leveraging the ACID properties of the databases. Additionally, the cache is configured by default to automatically
purge stale snapshots after 90 days.

> ⚠️ **Warning:** This SnapshotCache assumes that you already created the needed table with the specified schema and
> will fail on startup when this condition is not met.

You can run one of the following SQL scripts to create the necessary table:

**PostgreSQL**

```sql
CREATE TABLE IF NOT EXISTS factcast_snapshot
(
    projection_class       VARCHAR(512),
    aggregate_id           VARCHAR(36) NULL,
    last_fact_id           VARCHAR(36),
    bytes                  BYTEA,
    snapshot_serializer_id VARCHAR(128),
    PRIMARY KEY (projection_class, aggregate_id)
);
```

As well as a second table to store the timestamps of the last access per snapshot

```sql
CREATE TABLE IF NOT EXISTS factcast_snapshot_last_accessed
(
    projection_class VARCHAR(512),
    aggregate_id     VARCHAR(36) NULL,
    last_accessed    DATE        NOT NULL DEFAULT CURRENT_DATE,
    PRIMARY KEY (projection_class, aggregate_id)
);
CREATE INDEX IF NOT EXISTS factcast_snapshot_last_accessed_index ON factcast_snapshot_last_accessed USING BTREE (last_accessed);
```

**MySQL & MariaDB**

```sql
CREATE TABLE IF NOT EXISTS factcast_snapshot
(
    projection_class       VARCHAR(512) NOT NULL,
    aggregate_id           VARCHAR(36)  NULL,
    last_fact_id           VARCHAR(36)  NOT NULL,
    bytes                  BLOB,
    snapshot_serializer_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (projection_class, aggregate_id)
);
```

As well as a second table to store the timestamps of the last access per snapshot (see postgresql variant above).

**Oracle**

```sql
CREATE TABLE factcast_snapshot
(
    projection_class       VARCHAR2(512) NOT NULL,
    aggregate_id           VARCHAR2(36)  NULL,
    last_fact_id           VARCHAR2(36)  NOT NULL,
    bytes                  BLOB,
    snapshot_serializer_id VARCHAR2(128) NOT NULL,
    PRIMARY KEY (projection_class, aggregate_id)
);
```

As well as a second table to store the timestamps of the last access per snapshot (see postgresql variant above).

For further details, see the [JDBC Properties]({{< ref "/setup/properties#jdbc-snapshots">}}).

### MongoDB SnapshotCache

For applications utilizing MongoDB, the Mongo-based SnapshotCache offers simple solution for storing snapshots in a
centralized MongoDB collection which can be used by multiple instances of the same application.
The MongoDB SnapshotCache uses GridFS to store snapshot data, this means, that snapshots can be larger than 16MB.

```xml

<dependency>
    <groupId>org.factcast</groupId>
    <artifactId>factcast-snapshotcache-mongodb</artifactId>
</dependency>
```

By default, this cache automatically deletes stale snapshots after 90 days. But chunks stored by GridFS are not
automatically deleted, so you have to take care of that on your own.

For further details, see the [Mongo Properties]({{< ref "/setup/properties#mongodb-snapshots">}}).
