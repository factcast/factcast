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

Refer to the [In-Memory and Disk Properties]({{< ref "/setup/properties#inmemanddisk-snapshots">}}) for more information.

### Redis SnapshotCache

For applications utilizing Redis, the Redis-based SnapshotCache offers an optimal solution:

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

For applications with a JDBC-compatible database, the JDBC-based SnapshotCache is a viable option:

```xml
<dependency>
    <groupId>org.factcast</groupId>
    <artifactId>factcast-snapshotcache-jdbc</artifactId>
</dependency>
```

To use this snapshot cache, you must create a table for storing snapshots. The table schema is as follows:

```sql
# Postgres
CREATE TABLE factcast_snapshots(key VARCHAR(512), uuid VARCHAR(36), last_fact_id VARCHAR(36),
    bytes BYTEA, compressed boolean, last_accessed VARCHAR(10), PRIMARY KEY (key, uuid));
CREATE INDEX factcast_snapshots_idx_last_accessed ON factcast_snapshots(last_accessed);

# MySQL, MariaDB
CREATE TABLE factcast_snapshots(key VARCHAR(512), uuid VARCHAR(36), last_fact_id VARCHAR(36),
    bytes BLOB, compressed BOOLEAN, last_accessed VARCHAR(10), PRIMARY KEY (key, uuid));
CREATE INDEX factcast_snapshots_idx_last_accessed ON factcast_snapshots(last_accessed);

# Oracle
CREATE TABLE factcast_snapshots(key VARCHAR2(512), uuid VARCHAR2(36), last_fact_id VARCHAR2(36),
    bytes BLOB, compressed NUMBER(1), last_accessed VARCHAR(10), PRIMARY KEY (key, uuid));
CREATE INDEX factcast_snapshots_idx_last_accessed ON factcast_snapshots(last_accessed);

# MS SQL
CREATE TABLE factcast_snapshots(key NVARCHAR(512), uuid NVARCHAR(36), last_fact_id NVARCHAR(36),
    bytes VARBINARY(MAX), compressed BIT, last_accessed VARCHAR(10), PRIMARY KEY (key, uuid));
CREATE INDEX factcast_snapshots_idx_last_accessed ON factcast_snapshots(last_accessed);

# DB2
CREATE TABLE factcast_snapshots (key VARCHAR(512), uuid VARCHAR(36), last_fact_id VARCHAR(36),
    bytes BLOB, compressed SMALLINT, last_accessed VARCHAR(10), PRIMARY KEY (key, uuid));
CREATE INDEX factcast_snapshots_idx_last_accessed ON factcast_snapshots(last_accessed);
```

This option supports multiple instances of the same application, making it suitable for distributed environments. By
default, this cache automatically deletes stale snapshots after 90 days.

For further details, see the [JDBC Properties]({{< ref "/setup/properties#jdbc">}}).
