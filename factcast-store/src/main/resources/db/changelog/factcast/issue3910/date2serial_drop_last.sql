-- we need to keep the column around for a while, so that older instances continue working
ALTER TABLE date2serial
    ALTER COLUMN lastser DROP NOT NULL;
ALTER TABLE date2serial
    ALTER COLUMN lastser SET DEFAULT -1;


