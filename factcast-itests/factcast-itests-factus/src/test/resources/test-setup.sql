create table usernames(id UUID primary key, name varchar(255));
create table usernames_state(id int IDENTITY, state UUID);
create table some_events(id UUID primary key, name varchar(255));
create table some_events_state(id int IDENTITY, state UUID);
