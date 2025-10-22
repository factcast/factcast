-- this might take a while and create quite some I/O

ALTER table transformationcache
    drop column last_access;
