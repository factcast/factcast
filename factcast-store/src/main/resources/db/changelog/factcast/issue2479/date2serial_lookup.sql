CREATE TABLE IF NOT EXISTS date2serial (
        factDate date PRIMARY KEY,
        firstSer bigint not null,
        lastSer bigint not null
);
